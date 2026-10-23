package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.auth.PictureContentAuthManager;
import com.yuwen.visionspace.manager.cache.PictureActionRateLimiter;
import com.yuwen.visionspace.mapper.PictureActionEventDedupeMapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.picture.PictureActionReportCommand;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.model.enums.ActionSourceEnum;
import com.yuwen.visionspace.model.enums.ActionTypeEnum;
import com.yuwen.visionspace.model.vo.PictureActionReportMetricsVO;
import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 图片行为服务实现类
 * 负责将用户行为数据写入数据库，用于后续的推荐系统计算
 *
 * <p>公开入口的边界：
 * <ul>
 *   <li>目标必须当前对公开访客可见：公开图库里已审核通过、且没有被隔离的图片；
 *       私有、未审核、不存在与已隔离的目标一律拒绝。可性一律由集中入口
 *       {@link PictureContentAuthManager} 判定，这里不另写一套并行规则；</li>
 *   <li>匿名只允许曝光、点击、浏览；点赞、收藏等账户动作要求登录；</li>
 *   <li>客户端声明的用户 id 一律拒绝，身份只以服务端解析结果为准；</li>
 *   <li>事件幂等键保证同一事件重放不会重复入账：幂等依据写在单独的台账里，
 *       事件载荷按保留期清理之后，重放仍然被挡住；</li>
 *   <li>上报只写事件明细，不修改任何账户状态。</li>
 * </ul>
 *
 * @see PictureActionService 接口定义
 */
@Slf4j
@Service
public class PictureActionServiceImpl implements PictureActionService {

    /** 匿名允许上报的行为：曝光、点击、浏览 */
    private static final Set<ActionTypeEnum> ANONYMOUS_ALLOWED_ACTIONS = EnumSet.of(
            ActionTypeEnum.IMPRESSION, ActionTypeEnum.CLICK, ActionTypeEnum.VIEW);

    /** 客户端事件幂等键允许的字符 */
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    @Resource
    private UserPictureActionMapper actionMapper;

    @Resource
    private PictureActionEventDedupeMapper dedupeMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureContentAuthManager pictureContentAuthManager;

    @Resource
    private PictureActionRateLimiter rateLimiter;

    /** 没有事件幂等键时，同一客户端的相同行为在该窗口内视为同一事件 */
    @Value("${vision-space.picture-action.dedupe-window-seconds:60}")
    private long dedupeWindowSeconds;

    /**
     * 上报图片行为。
     *
     * @param command 已完成身份解析的上报命令
     * @return true 表示新事件已入库；false 表示幂等键命中的重复事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reportAction(PictureActionReportCommand command) {
        return doReportAction(command);
    }

    /**
     * 拒绝当前上报：在拒绝发生的分支上就地计数并抛出。
     *
     * <p>不用外层 catch 统一计数：限流分支已经计入限流，再按拒绝计数
     * 会把同一请求算进两个类别，可观测信息就失真了。
     */
    private void reject(ErrorCode errorCode, String message) {
        rateLimiter.countRejected();
        throw new BusinessException(errorCode, message);
    }

    private boolean doReportAction(PictureActionReportCommand command) {
        if (command == null) {
            reject(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        // 客户端不得声明身份：出现即拒绝，避免冒充他人上报
        if (command.getDeclaredUserId() != null) {
            reject(ErrorCode.PARAMS_ERROR, "客户端不得声明用户身份");
        }
        if (command.getPictureId() == null || command.getPictureId() <= 0) {
            reject(ErrorCode.PARAMS_ERROR, "图片ID不合法");
        }
        ActionTypeEnum actionType = ActionTypeEnum.fromValue(
                command.getActionType() == null ? -1 : command.getActionType());
        if (actionType == null) {
            reject(ErrorCode.PARAMS_ERROR, "行为类型不合法");
        }
        ActionSourceEnum source = ActionSourceEnum.fromValue(
                command.getSource() == null ? -1 : command.getSource());
        if (source == null) {
            reject(ErrorCode.PARAMS_ERROR, "行为来源不合法");
        }

        boolean anonymous = command.getActorUserId() == null;
        // 点赞、收藏等账户动作要求登录
        if (anonymous && !ANONYMOUS_ALLOWED_ACTIONS.contains(actionType)) {
            reject(ErrorCode.NO_AUTH_ERROR, "该行为需要登录后上报");
        }

        // 限流放在写库之前，异常流量不会压到数据库；只计限流，不重复计拒绝
        if (!rateLimiter.allow(command.getClientKey())) {
            rateLimiter.countRateLimited();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上报过于频繁，请稍后再试");
        }

        Picture picture = pictureMapper.selectById(command.getPictureId());
        if (picture == null) {
            reject(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        if (!isPubliclyVisible(picture)) {
            reject(ErrorCode.NO_AUTH_ERROR, "无权对该图片上报行为");
        }

        String eventId = resolveEventId(command, actionType, source);
        // 先登记幂等台账再写事件载荷：台账是重复判定的可靠来源，
        // 载荷按保留期清理之后，同一个事件重放仍然被挡住，不会二次入账。
        if (!registerEvent(eventId)) {
            // 同一事件重放：不重复入账，按成功受理返回
            rateLimiter.countDuplicate();
            log.info("行为事件重复上报已忽略: pictureId={}, actionType={}, source={}",
                    command.getPictureId(), actionType.getValue(), source.getValue());
            return false;
        }
        UserPictureAction action = new UserPictureAction();
        action.setUserId(anonymous ? 0L : command.getActorUserId());
        action.setPictureId(command.getPictureId());
        action.setActionType(actionType.getValue());
        action.setActionValue(1);
        action.setSource(source.getValue());
        action.setEventId(eventId);
        action.setIsProcessed(0);
        action.setIsAccumulated(0);
        action.setAttemptCount(0);
        try {
            actionMapper.insert(action);
        } catch (DuplicateKeyException e) {
            // 载荷上的唯一索引是最后一道防线：台账之外仍有重复时同样不入账
            rateLimiter.countDuplicate();
            log.info("行为事件重复上报已忽略: pictureId={}, actionType={}, source={}",
                    command.getPictureId(), actionType.getValue(), source.getValue());
            return false;
        }
        rateLimiter.countAccepted();
        log.debug("记录图片行为: userId={}, pictureId={}, actionType={}, source={}",
                action.getUserId(), action.getPictureId(), actionType.getValue(), source.getValue());
        return true;
    }

    @Override
    public PictureActionReportMetricsVO getReportMetrics() {
        return rateLimiter.metrics();
    }

    /**
     * 登记一次事件受理。
     *
     * @param eventId 事件幂等键
     * @return true 表示这是第一次受理；false 表示同一事件已经受理过
     */
    private boolean registerEvent(String eventId) {
        try {
            dedupeMapper.insertEvidence(eventId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * 目标必须当前对公开访客可见。
     *
     * <p>可见性一律由集中入口判定：公开图库里已审核通过的图片对匿名访客可见，
     * 未审核图片、空间图片与被隔离的资源都不可见。这里不再另写一套并行规则，
     * 隔离这类规则落在统一入口上时，公开行为上报随之一起生效。
     */
    private boolean isPubliclyVisible(Picture picture) {
        return pictureContentAuthManager.canReadContent(picture, null);
    }

    /**
     * 计算事件的幂等键。
     *
     * <p>客户端提供事件 id 时，按「客户端范围 + 事件 id」入库存放：
     * 同一个事件重放命中唯一索引被忽略，而别的客户端无法用别人的事件 id
     * 把事件挤掉。没有提供事件 id 时，按「客户端范围 + 目标 + 行为 + 来源 +
     * 时间窗」派生一个键，把短时间内的重复上报视为同一事件。
     */
    private String resolveEventId(PictureActionReportCommand command,
                                  ActionTypeEnum actionType, ActionSourceEnum source) {
        String scope = command.getClientKey();
        String raw = command.getEventId();
        if (raw != null && !raw.isBlank()) {
            if (!EVENT_ID_PATTERN.matcher(raw).matches()) {
                reject(ErrorCode.PARAMS_ERROR, "事件ID格式不合法");
            }
            return scope + ":" + raw;
        }
        long bucket = System.currentTimeMillis() / 1000 / Math.max(1L, dedupeWindowSeconds);
        String material = scope + '|' + command.getPictureId() + '|' + actionType.getValue()
                + '|' + source.getValue() + '|' + bucket;
        return HashUtils.sha256Hex(material);
    }
}
