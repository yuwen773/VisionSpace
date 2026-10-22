package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.service.PictureFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 用户级上传额度与速率限制。
 *
 * <p>两类限制互相独立：
 * <ul>
 *   <li>速率：同一用户在固定窗口内的上传次数，使用 Redis 计数（双实例共享）；</li>
 *   <li>额度：用户当前保留的临时/有效资源总字节数，来自资源记录。</li>
 * </ul>
 * Redis 不可用时按失败处理，不放行未校验的上传。
 */
@Slf4j
@Component
public class UploadQuotaManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureFileService pictureFileService;

    /** 每个用户每天的 Agent 临时上传次数 */
    @Value("${vision-space.upload.agent-daily-limit:100}")
    private long agentDailyLimit;

    /** 每个用户每天在图库中的上传次数 */
    @Value("${vision-space.upload.gallery-daily-limit:200}")
    private long galleryDailyLimit;

    /** 单个用户可保留的资源总字节数 */
    @Value("${vision-space.upload.max-total-bytes:1073741824}")
    private long maxTotalBytes;

    /**
     * 记录一次上传尝试并检查速率。超限时抛出 40000，不产生任何存储副作用。
     *
     * @param userId  用户 id
     * @param purpose 资源用途
     */
    public void checkRate(Long userId, PictureFilePurposeEnum purpose) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        long limit = purpose == PictureFilePurposeEnum.AGENT_CHAT ? agentDailyLimit : galleryDailyLimit;
        String key = "vision-space:upload-rate:" + purpose.getValue() + ":" + userId + ":"
                + LocalDate.now();
        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 窗口在首次写入时设置，避免计数永不过期
                stringRedisTemplate.expire(key, Duration.ofDays(1));
            }
        } catch (Exception e) {
            log.error("上传速率检查失败: userId={}, purpose={}", userId, purpose.getValue(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传暂时不可用");
        }
        if (count == null || count > limit) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传过于频繁，请稍后再试");
        }
    }

    /**
     * 检查用户已保留的资源总字节数。
     *
     * @param userId     用户 id
     * @param incomingBytes 本次将新增的字节数，未知时传 null 只检查现有占用
     */
    public void checkQuota(Long userId, Long incomingBytes) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        long used = pictureFileService.usedBytes(userId);
        long incoming = incomingBytes == null ? 0 : Math.max(0, incomingBytes);
        if (used + incoming > maxTotalBytes) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "存储空间不足");
        }
    }

    /**
     * 回滚一次速率计数（上传在校验阶段失败时使用），避免把失败请求算进配额。
     */
    public void refundRate(Long userId, PictureFilePurposeEnum purpose) {
        if (userId == null || purpose == null) {
            return;
        }
        String key = "vision-space:upload-rate:" + purpose.getValue() + ":" + userId + ":"
                + LocalDate.now();
        try {
            stringRedisTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            log.warn("上传速率回滚失败: userId={}", userId, e);
        }
    }

}
