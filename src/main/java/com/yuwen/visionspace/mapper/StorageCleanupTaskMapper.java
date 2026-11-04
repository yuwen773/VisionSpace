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
     *
     * <p>{@code pendingKey} 取拼接值的 SHA-256 十六进制摘要：对象键最长 768 字符，
     * 加上平台与类型后远超 255 字符的唯一索引列宽，直接拼接会被截断或报错；
     * 定长摘要也让 utf8mb4 下的索引长度保持可控。
     */
    @Insert("INSERT INTO storage_cleanup_task " +
            "(pictureFileId, pictureId, platform, objectKey, kind, reason, status, attempts, nextRetryTime, pendingKey) " +
            "VALUES (#{pictureFileId}, #{pictureId}, #{platform}, #{objectKey}, #{kind}, #{reason}, 0, 0, NOW(), " +
            "SHA2(CONCAT(COALESCE(#{platform}, ''), '|', COALESCE(#{kind}, ''), '|', #{objectKey}), 256)) " +
            "ON DUPLICATE KEY UPDATE updateTime = updateTime")
    int insertPending(@Param("pictureFileId") Long pictureFileId,
                      @Param("pictureId") Long pictureId,
                      @Param("platform") String platform,
                      @Param("objectKey") String objectKey,
                      @Param("kind") String kind,
                      @Param("reason") String reason);

    /**
     * 直接登记一条等待人工核对的终态任务：对象只有地址、没有平台与对象键，
     * 不进入自动重试，也不占用待处理唯一键。
     */
    @Insert("INSERT INTO storage_cleanup_task " +
            "(pictureFileId, pictureId, platform, objectKey, kind, reason, status, attempts, lastError) " +
            "SELECT NULL, NULL, NULL, #{objectKey}, NULL, #{reason}, 2, 0, #{lastError} " +
            "WHERE NOT EXISTS (SELECT 1 FROM storage_cleanup_task " +
            "WHERE objectKey = #{objectKey} AND status = 2 AND pictureFileId IS NULL)")
    int insertForReview(@Param("objectKey") String objectKey,
                        @Param("reason") String reason,
                        @Param("lastError") String lastError);

    /**
     * 到期待重试的任务。到期判定在数据库时钟内完成，与应用侧时钟无关。
     */
    @Select("SELECT * FROM storage_cleanup_task WHERE status = 0 " +
            "AND (nextRetryTime IS NULL OR nextRetryTime <= NOW()) ORDER BY id ASC LIMIT #{limit}")
    List<StorageCleanupTask> selectDue(@Param("limit") int limit);

    /**
     * 把超过认领时限的任务放回待重试，避免实例中断后任务永久卡在「处理中」。
     *
     * <p>只在待重试查询之前调用：认领时长按数据库时钟计算，与实例本地时钟无关。
     */
    @Update("UPDATE storage_cleanup_task SET status = 0, claimTime = NULL, " +
            "lastError = '认领超时，重新排队', updateTime = NOW() " +
            "WHERE status = 3 AND claimTime IS NOT NULL " +
            "AND claimTime < NOW() - INTERVAL #{timeoutSeconds} SECOND")
    int releaseStaleClaims(@Param("timeoutSeconds") long timeoutSeconds);

    /**
     * 原子认领一条任务：只有 status 仍为待重试时才会成功。
     *
     * <p>两个实例同时取到同一批到期任务时，条件更新保证只有一方拿到处理权，
     * 另一方影响 0 行并跳过；不依赖行锁或进程内状态，跨 JVM 同样成立。
     */
    @Update("UPDATE storage_cleanup_task SET status = 3, claimTime = NOW(), updateTime = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int claim(@Param("id") Long id);

    /**
     * 对象删除已确认。进入终态时释放待处理唯一键。
     */
    @Update("UPDATE storage_cleanup_task SET status = 1, attempts = attempts + 1, lastError = NULL, " +
            "claimTime = NULL, pendingKey = NULL, updateTime = NOW() " +
            "WHERE id = #{id} AND status = 3")
    int markDone(@Param("id") Long id);

    /**
     * 删除未确认：保留任务并按退避时间安排下次重试。
     */
    @Update("UPDATE storage_cleanup_task SET status = 0, attempts = attempts + 1, " +
            "lastError = #{lastError}, claimTime = NULL, " +
            "nextRetryTime = NOW() + INTERVAL #{delaySeconds} SECOND, updateTime = NOW() " +
            "WHERE id = #{id} AND status = 3")
    int markRetry(@Param("id") Long id,
                  @Param("lastError") String lastError,
                  @Param("delaySeconds") long delaySeconds);

    /**
     * 引用复核不通过：暂停自动重试，等待人工处理，同时释放待处理唯一键，
     * 让同一个对象之后可以按新的事实重新排队。
     */
    @Update("UPDATE storage_cleanup_task SET status = 2, attempts = attempts + 1, lastError = #{lastError}, " +
            "claimTime = NULL, pendingKey = NULL, updateTime = NOW() " +
            "WHERE id = #{id} AND status = 3")
    int markBlocked(@Param("id") Long id, @Param("lastError") String lastError);
}
