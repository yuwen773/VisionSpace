package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.dto.picture.PictureActionReportCommand;
import com.yuwen.visionspace.model.vo.PictureActionReportMetricsVO;

/**
 * 图片行为服务接口
 * 定义用户对图片行为的上报和处理能力
 *
 * @see PictureActionServiceImpl 实现类
 */
public interface PictureActionService {

    /**
     * 上报图片行为。
     *
     * <p>身份只以服务端解析结果为准：客户端声明的用户 id 一律拒绝。
     * 同一事件重放（幂等键相同）不会重复入账。
     *
     * @param command 已完成身份解析的上报命令
     * @return true 表示新事件已入库；false 表示幂等键命中的重复事件
     */
    boolean reportAction(PictureActionReportCommand command);

    /**
     * 行为上报的可观测计数（脱敏）。
     */
    PictureActionReportMetricsVO getReportMetrics();
}
