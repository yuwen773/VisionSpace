package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.PictureFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PictureFileMapper extends BaseMapper<PictureFile> {

    /**
     * 锁定读一条资源记录。
     *
     * <p>绑定、替换、删除与回收都先拿这条行锁：判定与写入之间不留窗口，
     * 多个应用实例之间由数据库保证互斥（不是只在单个 JVM 内生效）。
     */
    @Select("SELECT * FROM picture_file WHERE id = #{id} FOR UPDATE")
    PictureFile selectForUpdate(@Param("id") Long id);

    /**
     * 回收候选：用途为 Agent 会话、仍是临时状态、且创建时间早于保留期截止点。
     *
     * <p>「是否已绑定」不在这里判定：候选只是可能过期，回收前必须逐条锁定并
     * 复核绑定状态。
     *
     * <p>保留期用数据库自己的时钟计算（{@code NOW()}），不把应用侧的时间当作
     * 参数传入：{@code createTime} 由数据库写入，两侧必须用同一个时钟比较，
     * 否则时区或时钟漂移会让「已过期」判定整体偏移。
     *
     * @param retentionHours 保留期小时数
     * @param limit          单批上限
     */
    @Select("SELECT * FROM picture_file " +
            "WHERE purpose = 'agent-chat' AND status = 0 " +
            "AND createTime < NOW() - INTERVAL #{retentionHours} HOUR " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<PictureFile> selectReclaimCandidates(@Param("retentionHours") int retentionHours,
                                               @Param("limit") int limit);

    /**
     * 待重试的删除任务。对象存储删除未确认时保留在这里，由清理任务重试。
     *
     * <p>比较同样在数据库时钟内完成：{@code deleteNextRetryTime} 由数据库写入。
     */
    @Select("SELECT * FROM picture_file " +
            "WHERE deleteState = 1 AND (deleteNextRetryTime IS NULL OR deleteNextRetryTime <= NOW()) " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<PictureFile> selectPendingDeletes(@Param("limit") int limit);

    /**
     * 标记删除未确认，保留可重试状态。
     *
     * <p>下次重试时间由数据库时钟推导（{@code NOW()} 加退避时长），与应用侧时钟
     * 无关。
     */
    @Update("UPDATE picture_file SET deleteState = 1, deleteRetryCount = #{retryCount}, " +
            "deleteNextRetryTime = NOW() + INTERVAL #{retryDelaySeconds} SECOND, updateTime = NOW() WHERE id = #{id}")
    int markDeletePending(@Param("id") Long id,
                          @Param("retryCount") int retryCount,
                          @Param("retryDelaySeconds") long retryDelaySeconds);

    /**
     * 标记删除已确认：资源不再占用存储。
     */
    @Update("UPDATE picture_file SET status = 2, deleteState = 2, deleteRetryCount = #{retryCount}, " +
            "updateTime = NOW() WHERE id = #{id}")
    int markDeleteConfirmed(@Param("id") Long id, @Param("retryCount") int retryCount);

    /**
     * 标记资源为有效。Agent 附件绑定后不再是「无人引用的临时资源」，
     * 回收任务不会把它当成候选。
     */
    @Update("UPDATE picture_file SET status = 1, updateTime = NOW() WHERE id = #{id} AND status = 0")
    int markActive(@Param("id") Long id);
}
