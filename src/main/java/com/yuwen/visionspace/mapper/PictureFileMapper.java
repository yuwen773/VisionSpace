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
     * 锁定读一张图片当前的某状态资源记录。
     *
     * <p>替换路径用它挑出要被替换掉的旧记录：两个实例同时替换同一张图时，
     * 后到的一方在拿到行锁之后才看到前一方提交后的状态，不会把同一批旧对象
     * 再清理一遍。
     */
    @Select("SELECT * FROM picture_file WHERE pictureId = #{pictureId} AND status = #{status} FOR UPDATE")
    List<PictureFile> selectByPictureAndStatusForUpdate(@Param("pictureId") Long pictureId,
                                                       @Param("status") Integer status);

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

    /**
     * 同一对象键上仍占用存储的记录（临时或有效）。清理任务删除对象之前用它核对
     * 引用：已被替换或已删除的记录不再占用，不会被当成新的引用者。
     */
    @Select("SELECT * FROM picture_file WHERE platform = #{platform} AND objectKey = #{objectKey} " +
            "AND status IN (0, 1)")
    List<PictureFile> selectLiveByPlatformAndKey(@Param("platform") String platform,
                                                 @Param("objectKey") String objectKey);

    /**
     * 资源记录是否仍绑定在一张未被删除的图片上。清理任务据此判断「这张图片又被
     * 重新启用」，避免把仍在服务的内容删掉。
     */
    @Select("SELECT COUNT(*) FROM picture p JOIN picture_file f ON f.pictureId = p.id " +
            "WHERE f.id = #{fileId} AND p.isDelete = 0")
    int countLivePictureBinding(@Param("fileId") Long fileId);

    /**
     * 除自己之外，同一对象键还有多少条占用存储的记录（临时或有效）。
     *
     * <p>删除对象之前用它确认没有别的记录指向同一个键：同一张图的历史记录、
     * 或复制出来的记录都可能共用一个对象，删掉会让其余记录读到不存在的对象。
     */
    @Select("SELECT COUNT(*) FROM picture_file WHERE platform = #{platform} AND objectKey = #{objectKey} " +
            "AND status IN (0, 1) AND id <> #{excludeId}")
    int countLiveReferences(@Param("platform") String platform,
                            @Param("objectKey") String objectKey,
                            @Param("excludeId") Long excludeId);
}
