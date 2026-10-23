package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.mapper.PictureStatsMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.model.enums.ActionTypeEnum;
import com.yuwen.visionspace.model.vo.PictureStatsAggregateResultVO;
import com.yuwen.visionspace.model.vo.PictureStatsBacklogVO;
import com.yuwen.visionspace.service.PictureStatsAggregateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 图片行为聚合服务实现
 *
 * <p>一轮聚合按「领取 → 累计 → 完成」推进：
 * <ol>
 *   <li>领取：按持久状态挑出未完成、未计入、且没有有效租约的事件，写入本次令牌
 *       与租约到期时间。租约到期后其他实例可以重新领取；</li>
 *   <li>累计：在同一个事务里把事件增量累加到 picture_stats，并把这些事件标记为
 *       已计入。中断时两者一起回滚，不会出现「入了账却没标记」的半截状态；</li>
 *   <li>完成：只标记真正入账过的事件；</li>
 *   <li>收敛：上一轮在累计之后、完成之前中断的事件（已计入未完成）只补完成标记，
 *       不重新入账；</li>
 *   <li>清理：只删除已处理且超过保留期的事件，未处理事件留在库里。</li>
 * </ol>
 *
 * <p>没有进程内时间窗：消费范围完全由库里的状态决定，重启、重复批次与多实例
 * 竞争都按同一套规则处理。
 */
@Slf4j
@Service
public class PictureStatsAggregateServiceImpl implements PictureStatsAggregateService {

    @Resource
    private UserPictureActionMapper actionMapper;

    @Resource
    private PictureStatsMapper statsMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    /** 单批最多领取的事件数 */
    @Value("${vision-space.picture-stats.aggregate.batch-size:500}")
    private int batchSize;

    /** 领取租约时长（秒）：租约到期后事件可被其他实例重新领取 */
    @Value("${vision-space.picture-stats.aggregate.lease-seconds:60}")
    private int leaseSeconds;

    /** 已处理事件的保留天数 */
    @Value("${vision-space.picture-stats.retention.days:7}")
    private int retentionDays;

    /** 领取次数达到该值即视为反复失败，进入失败积压 */
    @Value("${vision-space.picture-stats.aggregate.stuck-threshold:3}")
    private int stuckThreshold;

    @Override
    public PictureStatsAggregateResultVO aggregateActionStats() {
        PictureStatsAggregateResultVO result = new PictureStatsAggregateResultVO();

        // 先收敛上一轮遗留的「已计入未完成」：这些事件已经入账，只能补完成标记
        result.setRecoveredCount(transactionTemplate.execute(
                status -> actionMapper.completeAccumulatedPending()));

        while (true) {
            String claimToken = UUID.randomUUID().toString();
            LocalDateTime claimedAt = LocalDateTime.now();
            LocalDateTime claimExpireAt = claimedAt.plusSeconds(Math.max(1, leaseSeconds));
            int claimed = actionMapper.claimPendingEvents(claimToken, claimedAt, claimExpireAt, batchSize);
            if (claimed == 0) {
                break;
            }
            result.setClaimedCount(result.getClaimedCount() + claimed);
            result.setAccumulatedCount(result.getAccumulatedCount() + accumulate(claimToken));
            result.setCompletedCount(result.getCompletedCount() + complete(claimToken));
            if (claimed < batchSize) {
                // 本批没有取满，说明没有更多可领取的事件
                break;
            }
        }

        result.setDeletedCount(cleanOldActions());
        log.info("图片行为聚合完成: 领取={}, 累计={}, 完成={}, 收敛={}, 清理={}",
                result.getClaimedCount(), result.getAccumulatedCount(), result.getCompletedCount(),
                result.getRecoveredCount(), result.getDeletedCount());
        return result;
    }

    @Override
    public PictureStatsBacklogVO getBacklog() {
        PictureStatsBacklogVO backlog = actionMapper.selectBacklog(stuckThreshold);
        if (backlog == null) {
            backlog = new PictureStatsBacklogVO();
        }
        backlog.setOrphanCount(actionMapper.countOrphanPending());
        return backlog;
    }

    /**
     * 累计：把本次领取到的事件增量累加到统计，并标记为已计入。
     * 两步在同一个事务里，中断时一起回滚。
     */
    private int accumulate(String claimToken) {
        Integer accumulated = transactionTemplate.execute(status -> {
            List<UserPictureAction> events = actionMapper.selectClaimedForUpdate(claimToken);
            if (events.isEmpty()) {
                return 0;
            }
            Map<Long, PictureActionStatsDTO> deltas = new LinkedHashMap<>();
            List<Long> ids = new ArrayList<>(events.size());
            for (UserPictureAction event : events) {
                PictureActionStatsDTO delta = deltas.computeIfAbsent(
                        event.getPictureId(), this::emptyDelta);
                applyEvent(delta, event);
                ids.add(event.getId());
            }
            for (PictureActionStatsDTO delta : deltas.values()) {
                statsMapper.upsertIncrement(delta);
            }
            actionMapper.markAccumulated(claimToken, ids);
            return events.size();
        });
        return accumulated == null ? 0 : accumulated;
    }

    /**
     * 完成：只标记真正入账过的事件。
     */
    private int complete(String claimToken) {
        Integer completed = transactionTemplate.execute(status -> actionMapper.markProcessed(claimToken));
        return completed == null ? 0 : completed;
    }

    /**
     * 清理：只删除已处理且超过保留期的事件，未处理事件不允许被丢弃。
     */
    private int cleanOldActions() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        return actionMapper.deleteOldActions(threshold);
    }

    private PictureActionStatsDTO emptyDelta(Long pictureId) {
        PictureActionStatsDTO delta = new PictureActionStatsDTO();
        delta.setPictureId(pictureId);
        delta.setImpressionCount(0L);
        delta.setClickCount(0L);
        delta.setViewCount(0L);
        delta.setLikeCount(0L);
        delta.setCollectCount(0L);
        delta.setDownloadCount(0L);
        delta.setShareCount(0L);
        return delta;
    }

    private void applyEvent(PictureActionStatsDTO delta, UserPictureAction event) {
        long value = event.getActionValue() == null ? 1L : event.getActionValue();
        ActionTypeEnum actionType = ActionTypeEnum.fromValue(
                event.getActionType() == null ? -1 : event.getActionType());
        if (actionType == null) {
            log.warn("忽略未知行为类型: actionType={}", event.getActionType());
            return;
        }
        switch (actionType) {
            case IMPRESSION -> delta.setImpressionCount(delta.getImpressionCount() + value);
            case CLICK -> delta.setClickCount(delta.getClickCount() + value);
            case VIEW -> delta.setViewCount(delta.getViewCount() + value);
            case LIKE -> delta.setLikeCount(delta.getLikeCount() + value);
            case COLLECT -> delta.setCollectCount(delta.getCollectCount() + value);
            case DOWNLOAD -> delta.setDownloadCount(delta.getDownloadCount() + value);
            case SHARE -> delta.setShareCount(delta.getShareCount() + value);
        }
    }
}
