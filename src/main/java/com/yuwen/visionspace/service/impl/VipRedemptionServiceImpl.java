package com.yuwen.visionspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.UserMapper;
import com.yuwen.visionspace.mapper.VipRedemptionCodeMapper;
import com.yuwen.visionspace.mapper.VipRedemptionRecordMapper;
import com.yuwen.visionspace.model.dto.user.VipExchangeResult;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.VipRedemptionCode;
import com.yuwen.visionspace.model.entity.VipRedemptionRecord;
import com.yuwen.visionspace.service.VipRedemptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;

/**
 * VIP 兑换核销实现（#39）。
 *
 * <p>并发与事务设计：
 *
 * <ul>
 *   <li><b>同码跨实例只成功一次</b>：核销是单条
 *       {@code UPDATE vip_redemption_code SET status = 1 WHERE codeHash = ? AND status = 0}，
 *       行级锁与 WHERE 条件保证即使两个实例同时提交，也只有一条语句影响到 1 行。
 *       影响行数为 0 的提交方读状态得到明确「已用/无效」，不会误报成功。</li>
 *   <li><b>失败不吞码</b>：核销、用户权益更新、流水写入在同一个
 *       {@link Transactional} 事务内；任何一步抛错整体回滚，码回到可用状态。</li>
 *   <li><b>重试不重复延长</b>：已用码直接返回 {@code already_used}，不加时；
 *       同一用户两个不同码并发时，用户行在事务内以
 *       {@code SELECT ... FOR UPDATE} 锁定，两次加时串行读改写，都完整生效。</li>
 *   <li><b>权益独立于角色</b>：只写 {@code vipExpireTime}，永不触碰 {@code userRole}。</li>
 * </ul>
 */
@Service
@Slf4j
public class VipRedemptionServiceImpl extends ServiceImpl<VipRedemptionCodeMapper, VipRedemptionCode>
        implements VipRedemptionService {

    private final VipRedemptionCodeMapper codeMapper;
    private final VipRedemptionRecordMapper recordMapper;
    private final UserMapper userMapper;

    public VipRedemptionServiceImpl(VipRedemptionCodeMapper codeMapper,
                                    VipRedemptionRecordMapper recordMapper,
                                    UserMapper userMapper) {
        this.codeMapper = codeMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String redeem(User user, String vipCode) {
        if (user == null || user.getId() == null || StrUtil.isBlank(vipCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String codeHash = sha256(vipCode);

        // 1. 单语句原子核销：跨实例只成功一次
        int claimed = codeMapper.markUsed(codeHash, user.getId(), new Date());
        if (claimed == 0) {
            // 没抢到：区分已用与无效，向用户表达明确状态
            VipRedemptionCode existing = codeMapper.selectOne(
                    new QueryWrapper<VipRedemptionCode>().eq("codeHash", codeHash));
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() == VipRedemptionCode.STATUS_USED) {
                log.info("vip code already redeemed, userId={}, codeHash={}", user.getId(), shortHash(codeHash));
                return VipExchangeResult.STATUS_ALREADY_USED;
            }
            log.info("vip code invalid, userId={}, codeHash={}", user.getId(), shortHash(codeHash));
            return VipExchangeResult.STATUS_INVALID;
        }

        // 2. 锁定用户行：同用户并发兑换不同码时加时串行生效，不丢加时
        Date previousExpire = userMapper.selectVipExpireTimeForUpdate(user.getId());
        Date now = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        // 从当前时间与原到期时间中较晚者起算延长十二个月
        Date base = previousExpire != null && previousExpire.after(now) ? previousExpire : now;
        Date newExpire = Date.from(base.toInstant().plus(365, ChronoUnit.DAYS));

        // 3. 权益更新：只改到期时间，不触碰 userRole（VIP 独立于管理角色）
        int updated = userMapper.updateVipExpireTime(user.getId(), newExpire);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "开通会员失败，请重试");
        }

        // 4. 流水：与核销同事务；写失败整体回滚，码不被吞
        VipRedemptionRecord record = new VipRedemptionRecord();
        record.setUserId(user.getId());
        Long codeId = codeMapper.selectIdByHash(codeHash);
        record.setCodeId(codeId);
        record.setCodeHash(codeHash);
        record.setPreviousExpireTime(previousExpire);
        record.setExpireTime(newExpire);
        if (recordMapper.insert(record) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "记录兑换流水失败，请重试");
        }

        log.info("vip redeemed, userId={}, codeHash={}, expireTime={}", user.getId(), shortHash(codeHash), newExpire);
        return VipExchangeResult.STATUS_SUCCESS;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 日志只带摘要前缀，禁止输出明文码或完整摘要（摘要仍可用于撞库弱码）。 */
    private static String shortHash(String hash) {
        return hash.substring(0, 8);
    }
}
