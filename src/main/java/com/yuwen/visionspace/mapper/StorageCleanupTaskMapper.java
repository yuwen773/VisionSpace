package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.StorageCleanupTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StorageCleanupTaskMapper extends BaseMapper<StorageCleanupTask> {

    /**
     * 登记一条待重试的清理任务。
     *
     * <p>同一平台、同一内容类型、同一对象键只保留一条待处理任务：重复登记不会让
     * 同一个对象被排队删除多次，也不会在第一次失败后多出一批重复重试。
     * 进入终态的任务会把 {@code pendingKey} 置空，因此之后可以重新排队。
     */
    @Insert("INSERT INTO storage_cleanup_task " +
            "(pictureFileId, pictureId, platform, objectKey, kind, reason, status, attempts, nextRetryTime, pendingKey) " +
            "VALUES (#{pictureFileId}, #{pictureId}, #{platform}, #{objectKey}, #{kind}, #{reason}, 0, 0, NOW(), " +
            "CONCAT(COALESCE(#{platform}, ''), '|', COALESCE(#{kind}, ''), '|', #{objectKey})) " +
            "ON DUPLICATE KEY UPDATE updateTime = updateTime")
    int insertPending(@Param("pictureFileId") Long pictureFileId,
                      @Param("pictureId") Long pictureId,
                      @Param("platform") String platform,
                      @Param("objectKey") String objectKey,
                      @Param("kind") String kind,
                      @Param("reason") String reason);

    /**
     * 到期待重试的任务。到期判定在数据库时钟内完成，与应用侧时钟无关。
     */
    @Select("SELECT * FROM storage_cleanup_task WHERE status = 0 " +
            "AND (nextRetryTime IS NULL OR nextRetryTime <= NOW()) ORDER BY id ASC LIMIT #{limit}")
    List<StorageCleanupTask> selectDue(@Param("limit") int limit);

    /**
     * 对象删除已确认。进入终态时释放待处理唯一键。
     */
    @Update("UPDATE storage_cleanup_task SET status = 1, attempts = attempts + 1, lastError = NULL, " +
            "pendingKey = NULL, updateTime = NOW() WHERE id = #{id}")
    int markDone(@Param("id") Long id);

    /**
     * 删除未确认：保留任务并按退避时间安排下次重试。
     */
    @Update("UPDATE storage_cleanup_task SET attempts = attempts + 1, lastError = #{lastError}, " +
            "nextRetryTime = NOW() + INTERVAL #{delaySeconds} SECOND, updateTime = NOW() WHERE id = #{id}")
    int markRetry(@Param("id") Long id,
                  @Param("lastError") String lastError,
                  @Param("delaySeconds") long delaySeconds);

    /**
     * 引用复核不通过：暂停自动重试，等待人工处理，同时释放待处理唯一键，
     * 让同一个对象之后可以按新的事实重新排队。
     */
    @Update("UPDATE storage_cleanup_task SET status = 2, attempts = attempts + 1, lastError = #{lastError}, " +
            "pendingKey = NULL, updateTime = NOW() WHERE id = #{id}")
    int markBlocked(@Param("id") Long id, @Param("lastError") String lastError);
}
