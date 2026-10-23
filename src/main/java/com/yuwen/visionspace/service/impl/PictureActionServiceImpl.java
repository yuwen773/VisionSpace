package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.cache.PictureActionRateLimiter;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.picture.PictureActionReportCommand;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.model.enums.ActionSourceEnum;
import com.yuwen.visionspace.model.enums.ActionTypeEnum;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.vo.PictureActionReportMetricsVO;
import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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
 *   <li>目标必须是公开图库里已审核通过、且未删除的图片；私有、未审核、
 *       不存在的目标一律拒绝；</li>
 *   <li>匿名只允许曝光、点击、浏览；点赞、收藏等账户动作要求登录；</li>
 *   <li>客户端声明的用户 id 一律拒绝，身份只以服务端解析结果为准；</li>
 *   <li>事件幂等键保证同一事件重放不会重复入账；</li>
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
    private PictureMapper pictureMapper;

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
    public boolean reportAction(PictureActionReportCommand command) {
        try {
            return doReportAction(command);
        } catch (BusinessException e) {
            // 被拒绝的请求同样计数，异常流量才看得见
            rateLimiter.countRejected();
            throw e;
        }
    }

    private boolean doReportAction(PictureActionReportCommand command) {
        ThrowUtils.throwIf(command == null, ErrorCode.PARAMS_ERROR);
        // 客户端不得声明身份：出现即拒绝，避免冒充他人上报
        ThrowUtils.throwIf(command.getDeclaredUserId() != null, ErrorCode.PARAMS_ERROR,
                "客户端不得声明用户身份");
        ThrowUtils.throwIf(command.getPictureId() == null || command.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片ID不合法");
        ActionTypeEnum actionType = ActionTypeEnum.fromValue(
                command.getActionType() == null ? -1 : command.getActionType());
        ThrowUtils.throwIf(actionType == null, ErrorCode.PARAMS_ERROR, "行为类型不合法");
        ActionSourceEnum source = ActionSourceEnum.fromValue(
                command.getSource() == null ? -1 : command.getSource());
        ThrowUtils.throwIf(source == null, ErrorCode.PARAMS_ERROR, "行为来源不合法");

        boolean anonymous = command.getActorUserId() == null;
        // 点赞、收藏等账户动作要求登录
        ThrowUtils.throwIf(anonymous && !ANONYMOUS_ALLOWED_ACTIONS.contains(actionType),
                ErrorCode.NO_AUTH_ERROR, "该行为需要登录后上报");

        // 限流放在写库之前，异常流量不会压到数据库
        if (!rateLimiter.allow(command.getClientKey())) {
            rateLimiter.countRateLimited();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上报过于频繁，请稍后再试");
        }

        Picture picture = pictureMapper.selectById(command.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(!isPublicApproved(picture), ErrorCode.NO_AUTH_ERROR, "无权对该图片上报行为");

        String eventId = resolveEventId(command, actionType, source);
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
            // 同一事件重放：不重复入账，按成功受理返回
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
     * 只有公开图库里已审核通过的图片可以被上报行为。
     * 空间图片（含私有与团队空间）与未审核图片都不在公开行为范围内。
     */
    private boolean isPublicApproved(Picture picture) {
        return picture.getSpaceId() == null
                && picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
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
            ThrowUtils.throwIf(!EVENT_ID_PATTERN.matcher(raw).matches(), ErrorCode.PARAMS_ERROR,
                    "事件ID格式不合法");
            return scope + ":" + raw;
        }
        long bucket = System.currentTimeMillis() / 1000 / Math.max(1L, dedupeWindowSeconds);
        String material = scope + '|' + command.getPictureId() + '|' + actionType.getValue()
                + '|' + source.getValue() + '|' + bucket;
        return HashUtils.sha256Hex(material);
    }
}
