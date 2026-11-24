package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 公开图库索引待重试任务。
 */
@Mapper
public interface PictureIndexTaskMapper extends BaseMapper<PictureIndexTask> {

    /**
     * 记录一次失败的索引同步，等待重试。
     *
     * <p>同一张图片只有一条记录：再次失败时覆盖，重试成功后不再留下待办。
     *
     * @param pictureId     图片 id
     * @param operation     失败的动作：UPSERT / DELETE
     * @param status        状态
     * @param attempts      已尝试次数
     * @param lastError     失败原因（脱敏后）
     * @param nextRetryTime 下次重试时间
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO picture_index_task
                (picture_id, operation, status, attempts, last_error, next_retry_time, create_time, update_time)
            VALUES (#{pictureId}, #{operation}, #{status}, #{attempts}, #{lastError}, #{nextRetryTime},
                    statement_timestamp(), statement_timestamp())
            ON CONFLICT (picture_id) DO UPDATE SET
                operation = EXCLUDED.operation,
                status = EXCLUDED.status,
                attempts = EXCLUDED.attempts,
                last_error = EXCLUDED.last_error,
                next_retry_time = EXCLUDED.next_retry_time,
                update_time = statement_timestamp()
            """)
    int recordFailure(@Param("pictureId") Long pictureId,
                      @Param("operation") String operation,
                      @Param("status") Integer status,
                      @Param("attempts") Integer attempts,
                      @Param("lastError") String lastError,
                      @Param("nextRetryTime") Date nextRetryTime);

    /**
     * 取出到期待重试的任务。
     *
     * @param status 状态
     * @param limit  最多取出的条数
     * @return 到期的任务
     */
    @Select("""
            SELECT id, picture_id, operation, status, attempts, last_error, next_retry_time, create_time, update_time
            FROM picture_index_task
            WHERE status = #{status} AND next_retry_time IS NOT NULL
              AND next_retry_time <= statement_timestamp()
            ORDER BY next_retry_time ASC
            LIMIT #{limit}
            """)
    List<PictureIndexTask> selectRetryable(@Param("status") Integer status, @Param("limit") int limit);

    /**
     * 同步成功后清掉待办。
     *
     * @param pictureId 图片 id
     * @return 影响行数
     */
    @Delete("DELETE FROM picture_index_task WHERE picture_id = #{pictureId}")
    int deleteByPictureId(@Param("pictureId") Long pictureId);
}
