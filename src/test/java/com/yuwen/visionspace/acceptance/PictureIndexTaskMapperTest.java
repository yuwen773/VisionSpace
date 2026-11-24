package com.yuwen.visionspace.acceptance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.mapper.PictureIndexTaskMapper;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 索引失败待办的持久化语义，跑在真实 PostgreSQL 上。
 *
 * <p>失败记录是「写入失败之后还能恢复」的唯一凭据，它的 SQL 不能用替身验证：
 * 同一张图片反复失败必须收敛成一行，到期查询必须真的按时钟过滤。
 */
@IsolatedSpringBootTest
class PictureIndexTaskMapperTest {

    private static final long PICTURE_ID = 87820;

    @Autowired
    private PictureIndexTaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM picture_index_task WHERE picture_id = ?", PICTURE_ID);
    }

    @Test
    void repeatedFailuresCollapseIntoOneRowAndKeepTheLatestAttempt() {
        taskMapper.recordFailure(PICTURE_ID, "UPSERT", PictureIndexTask.STATUS_FAILED, 1, "第一次",
                due(-1));
        taskMapper.recordFailure(PICTURE_ID, "DELETE", PictureIndexTask.STATUS_FAILED, 2, "第二次",
                due(5));

        List<PictureIndexTask> rows = rowsOf(PICTURE_ID);
        assertEquals(1, rows.size(), "同一张图片只留一条待办，否则重试会被同一张图反复占满");
        PictureIndexTask row = rows.getFirst();
        assertEquals(2, row.getAttempts());
        assertEquals("DELETE", row.getOperation(), "动作以最近一次为准");
        assertEquals("第二次", row.getLastError());
    }

    @Test
    void retrySelectionHonoursTheBackoffDeadline() {
        taskMapper.recordFailure(PICTURE_ID, "UPSERT", PictureIndexTask.STATUS_FAILED, 1, "到期",
                due(-1));
        taskMapper.recordFailure(PICTURE_ID + 1, "UPSERT", PictureIndexTask.STATUS_FAILED, 1, "未到期",
                due(30));
        try {
            List<PictureIndexTask> ready = taskMapper.selectRetryable(PictureIndexTask.STATUS_FAILED, 50);

            assertTrue(ready.stream().anyMatch(task -> task.getPictureId() == PICTURE_ID),
                    "到期的待办必须被取出");
            assertTrue(ready.stream().noneMatch(task -> task.getPictureId() == PICTURE_ID + 1),
                    "没到退避时间的不能提前重试");
        } finally {
            jdbc.update("DELETE FROM picture_index_task WHERE picture_id = ?", PICTURE_ID + 1);
        }
    }

    @Test
    void successfulSyncClearsThePendingRow() {
        taskMapper.recordFailure(PICTURE_ID, "UPSERT", PictureIndexTask.STATUS_FAILED, 1, "失败", due(-1));

        taskMapper.deleteByPictureId(PICTURE_ID);

        assertEquals(0, rowsOf(PICTURE_ID).size(), "同步成功后不该再留下待办");
    }

    private List<PictureIndexTask> rowsOf(long pictureId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PictureIndexTask>()
                .eq(PictureIndexTask::getPictureId, pictureId));
    }

    private Date due(int minutesFromNow) {
        return new Date(System.currentTimeMillis() + minutesFromNow * 60_000L);
    }
}
