package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户图片行为明细表
 * 用于记录用户对图片的各种行为，为推荐系统提供基础数据
 *
 * <p>状态机（持久化在库里，不依赖进程内状态）：
 * <pre>
 *   待处理      isProcessed=0, isAccumulated=0
 *   已领取      isProcessed=0, isAccumulated=0, claimToken/claimExpireAt 有值
 *   已计入统计  isProcessed=0, isAccumulated=1
 *   已完成      isProcessed=1, isAccumulated=1
 * </pre>
 * 「已计入统计」与「已完成」分开记录，才能在两阶段之间崩溃后既不重复入账，
 * 也不把已入账的事件当成未处理重新累计。
 *
 * @see com.yuwen.visionspace.model.enums.ActionTypeEnum 行为类型枚举
 * @see com.yuwen.visionspace.model.enums.ActionSourceEnum 来源枚举
 */
@Data
@TableName("user_picture_action")
public class UserPictureAction {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID，0表示匿名用户（未登录）
     */
    private Long userId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share
     */
    private Integer actionType;

    /**
     * 行为值，默认为1
     */
    private Integer actionValue;

    /**
     * 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER
     */
    private Integer source;

    /**
     * 事件幂等键。同一事件重放时唯一索引会挡住第二次入账。
     */
    private String eventId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 是否已完成聚合：0-未处理 1-已处理
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer isProcessed;

    /**
     * 是否已计入统计：0-未计入 1-已计入
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer isAccumulated;

    /**
     * 领取令牌：持有本次领取租约的聚合运行
     */
    private String claimToken;

    /**
     * 领取时间
     */
    private LocalDateTime claimedAt;

    /**
     * 领取租约到期时间，过期后其他实例可以重新领取
     */
    private LocalDateTime claimExpireAt;

    /**
     * 领取尝试次数，反复失败的事件据此可见
     */
    private Integer attemptCount;
}
