package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.vo.PictureStatsAggregateResultVO;
import com.yuwen.visionspace.model.vo.PictureStatsBacklogVO;

/**
 * 图片行为聚合服务
 * 负责将 user_picture_action 明细数据聚合到 picture_stats
 *
 * <p>聚合按「领取 → 累计 → 完成」三个阶段推进，状态全部落在库里：
 * <ul>
 *   <li>领取带租约，实例崩溃后租约到期即可被重新领取，不会永久卡住；</li>
 *   <li>累计与「已计入」标记在同一个事务里，中断后要么都没发生，要么都发生了；</li>
 *   <li>完成只标记真正入账过的事件；</li>
 *   <li>只按持久状态消费，不依赖进程内时间窗，旧的未处理事件不会被跳过。</li>
 * </ul>
 */
public interface PictureStatsAggregateService {

    /**
     * 执行一轮聚合。
     *
     * @return 本轮的处理结果（脱敏计数）
     */
    PictureStatsAggregateResultVO aggregateActionStats();

    /**
     * 待处理与失败积压的可观测信息（脱敏计数）。
     */
    PictureStatsBacklogVO getBacklog();
}
