package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.model.vo.PictureStatsBacklogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户图片行为 Mapper
 * 继承 MyBatis Plus 的 BaseMapper，获得基本的 CRUD 能力
 *
 * <p>聚合相关语句都围绕持久状态工作：领取靠租约、累计与完成分开记录，
 * 不依赖进程内时间窗。
 *
 * @see UserPictureAction 用户图片行为实体
 */
@Mapper
public interface UserPictureActionMapper extends BaseMapper<UserPictureAction> {

    /**
     * 领取待处理事件。
     *
     * <p>只领取「未完成、未计入、且没有有效租约」的事件；目标图片已不存在的事件
     * 不在这里领取，它们留在待处理状态里作为冲突可见，不会被静默丢弃。
     *
     * <p>租约的时间基准一律取数据库时钟（{@code NOW()}）：租约到期时间是写进库的值，
     * 如果改用调用方 JVM 的时间去比较，容器与宿主、多个实例之间的时钟偏差就会让
     * 「租约是否过期」失真——快钟提前抢走别人的事件，慢钟让事件迟迟无法回收。
     * 写库与比较必须是同一个时钟。
     *
     * @param claimToken 本次领取的令牌
     * @param leaseSeconds 租约时长（秒）
     * @param limit 本次最多领取的事件数
     * @return 实际领取的事件数
     */
    int claimPendingEvents(@Param("claimToken") String claimToken,
                           @Param("leaseSeconds") int leaseSeconds,
                           @Param("limit") int limit);

    /**
     * 取出本次领取到、且尚未计入统计的事件并加行锁。
     *
     * @param claimToken 领取令牌
     * @return 待累计的事件
     */
    List<UserPictureAction> selectClaimedForUpdate(@Param("claimToken") String claimToken);

    /**
     * 把本次领取的事件标记为已计入统计。
     *
     * @param claimToken 领取令牌
     * @param ids 事件 id
     * @return 标记条数
     */
    int markAccumulated(@Param("claimToken") String claimToken, @Param("ids") List<Long> ids);

    /**
     * 完成本次领取的事件。只标记真正计入过统计的事件。
     *
     * @param claimToken 领取令牌
     * @return 完成条数
     */
    int markProcessed(@Param("claimToken") String claimToken);

    /**
     * 收敛「已计入统计但未完成」的事件。
     *
     * <p>这类事件来自上一次运行在累计之后、完成之前中断：它们已经入账，
     * 只能补完成标记，不能重新入账。
     *
     * @return 收敛条数
     */
    int completeAccumulatedPending();

    /**
     * 统计待处理积压。
     *
     * @param stuckThreshold 判定为反复失败的领取次数阈值
     * @return 积压计数
     */
    PictureStatsBacklogVO selectBacklog(@Param("stuckThreshold") int stuckThreshold);

    /**
     * 统计目标图片已不存在却仍未处理的事件。
     *
     * @return 冲突事件数
     */
    long countOrphanPending();

    /**
     * 删除已处理且超过保留期的 action 数据。
     *
     * <p>只清理已处理的事件：未处理的事件属于待处理积压，不能被定期清理丢弃。
     *
     * @param threshold 时间阈值
     * @return 删除条数
     */
    int deleteOldActions(@Param("threshold") LocalDateTime threshold);
}
