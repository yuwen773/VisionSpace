package com.yuwen.visionspace.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.mapper.UserPreferenceMapper;
import com.yuwen.visionspace.model.dto.agent.PreferenceViewVO;
import com.yuwen.visionspace.model.entity.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户长期偏好的并发与优先级语义单元测试（不起 Spring 上下文，不触真实模型）。
 *
 * <p>覆盖工单 #42 的三个易错点：自动学习只在明确开启后发生（27.2）、
 * 显式设置优先于模型推断且学习不改写显式设置（27.3）、
 * 清除/关闭与在途学习交错时旧任务不能复活已清除数据（27.3）。
 * SQL 条件本身的真实行为由隔离环境集成测试覆盖。
 */
class UserPreferenceServiceTest {

    private static final long USER_ID = 42L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private UserPreferenceMapper mapper;
    private UserPreferenceService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(UserPreferenceMapper.class);
        service = new UserPreferenceService(mapper, objectMapper);
        ReflectionTestUtils.setField(service, "agentConstants", new AgentConstants());
    }

    private UserPreference row(long version, long epoch, int autoLearn) {
        UserPreference row = new UserPreference();
        row.setUserId(USER_ID);
        row.setPreferenceVersion(version);
        row.setDataEpoch(epoch);
        row.setAutoLearn(autoLearn);
        return row;
    }

    // ---- 27.2 自动学习仅在明确开启后发生 ----

    @Test
    void saveFeedback_autoLearnOff_skipsLearningAndHistory() throws Exception {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 0));

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太亮了");

        verify(mapper, never()).applyLearned(anyLong(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void saveFeedback_autoLearnOn_recordsHistoryAndInference() throws Exception {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(1);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太亮了，换个赛博朋克风格");

        ArgumentCaptor<String> learnedJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> historyJson = ArgumentCaptor.forClass(String.class);
        verify(mapper).applyLearned(eq(USER_ID), learnedJson.capture(), historyJson.capture(),
                eq(0L), eq(0L));
        Map<String, Object> learned = objectMapper.readValue(learnedJson.getValue(), MAP_TYPE);
        assertTrue(learned.get("liked_styles").toString().contains("赛博朋克"), learnedJson.getValue());
        assertTrue(learned.get("disliked_styles").toString().contains("过亮色调"), learnedJson.getValue());
        assertTrue(historyJson.getValue().contains("REGENERATE"));
    }

    @Test
    void saveFeedback_capsHistoryAtConfiguredSize() throws Exception {        AgentConstants constants = new AgentConstants();
        constants.setMaxFeedbackHistorySize(1);
        ReflectionTestUtils.setField(service, "agentConstants", constants);

        UserPreference row = row(0, 0, 1);
        row.setFeedbackHistory("[{\"time\":1,\"action\":\"REGENERATE\",\"reason\":\"旧\"}]");
        when(mapper.selectByUserId(USER_ID)).thenReturn(row);
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(1);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "新原因");

        ArgumentCaptor<String> historyJson = ArgumentCaptor.forClass(String.class);
        verify(mapper).applyLearned(eq(USER_ID), anyString(), historyJson.capture(), eq(0L), eq(0L));
        List<Map<String, Object>> history = objectMapper.readValue(historyJson.getValue(),
                new TypeReference<>() {});
        assertEquals(1, history.size());
        assertEquals("新原因", history.get(0).get("reason"));
    }

    @Test
    void saveFeedback_nullContentRow_learnsIntoEmptyProfileWithoutFailure() throws Exception {
        // 刚清除或刚建行后内容列全为 null：学习必须按空档案处理——
        // 不抛错、不给 applyLearned 传 null，学习产物落成合法 JSON
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(1);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        ArgumentCaptor<String> learnedJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> historyJson = ArgumentCaptor.forClass(String.class);
        verify(mapper).applyLearned(eq(USER_ID), learnedJson.capture(), historyJson.capture(),
                eq(0L), eq(0L));
        Map<String, Object> learned = objectMapper.readValue(learnedJson.getValue(), MAP_TYPE);
        assertTrue(learned.get("disliked_styles").toString().contains("暗色调"), learnedJson.getValue());
        assertTrue(historyJson.getValue().contains("太暗了"), historyJson.getValue());
    }

    // ---- 27.3 清除/关闭与在途学习交错：旧任务不能复活已清除数据 ----

    @Test
    void saveFeedback_casConflictAfterClear_abortsAndNeverRewrites() throws Exception {
        // 第一次读到学习开启；学习写入前用户执行了清除（版本与纪元都已递增）
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1), row(1, 1, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(0);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        // 只写了一次且失败：清除后的重读绝不触发第二次写入，
        // 纪元已更替的终止也不算学习失败——不得记 learnError
        verify(mapper, times(1)).applyLearned(anyLong(), anyString(), anyString(), anyLong(), anyLong());
        verify(mapper, never()).recordLearnFailure(anyLong(), anyString());
    }

    @Test
    void saveFeedback_casConflictAfterDisable_abortsAndNeverRewrites() throws Exception {
        // 与清除同理：关闭后 autoLearn = 0，重读直接终止
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1), row(1, 1, 0));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(0);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        verify(mapper, times(1)).applyLearned(anyLong(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void saveFeedback_casConflictFromConcurrentLearning_retriesWithFreshVersion() throws Exception {
        // 并发的另一次学习写入只递增版本、不动纪元：重读后按新版本重试成功
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1), row(1, 0, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(0);
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(1L), eq(0L))).thenReturn(1);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        verify(mapper).applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L));
        verify(mapper).applyLearned(eq(USER_ID), anyString(), anyString(), eq(1L), eq(0L));
    }

    @Test
    void saveFeedback_repeatedCasConflictWithSameEpoch_givesUpObservably() {
        // 每次重读都是同纪元、开关仍开（真实的持续竞争）：放弃后必须留下
        // 可观察的失败记录，而不是静默吞掉这次反馈的学习
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(0);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        verify(mapper, times(3)).applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L));
        verify(mapper).recordLearnFailure(eq(USER_ID), eq("LEARN_GAVE_UP_CAS_CONFLICT"));
    }

    // ---- 27.3 显式设置优先于模型推断 ----

    @Test
    void loadForModel_nullContentRow_returnsEmptyWithoutFailure() {
        UserPreference row = row(0, 0, 0);
        row.setLearnedContent(null);
        row.setExplicitContent(null);
        when(mapper.selectByUserId(USER_ID)).thenReturn(row);

        assertTrue(service.loadForModel(USER_ID).isEmpty(), "内容列为 null 必须按空偏好处理");
    }

    @Test
    void loadForModel_explicitOverridesLearnedOnSameKey() {
        UserPreference row = row(0, 0, 1);
        row.setLearnedContent("{\"liked_styles\":[\"赛博朋克\"],\"liked_colors\":[\"暗色系\"]}");
        row.setExplicitContent("{\"liked_styles\":[\"水彩\"]}");
        when(mapper.selectByUserId(USER_ID)).thenReturn(row);

        Map<String, Object> merged = service.loadForModel(USER_ID);

        assertEquals(List.of("水彩"), merged.get("liked_styles"), "显式设置必须覆盖同名推断键");
        assertEquals(List.of("暗色系"), merged.get("liked_colors"), "推断键在显式未覆盖时保留");
    }

    @Test
    void saveFeedback_learningWritesOnlyLearnedColumns_neverExplicit() {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 1));
        when(mapper.applyLearned(eq(USER_ID), anyString(), anyString(), eq(0L), eq(0L))).thenReturn(1);

        service.saveFeedback(USER_ID, ActionType.REGENERATE, "太暗了");

        // applyLearned 的 SQL 只更新 learnedContent/feedbackHistory；
        // 学习路径不得调用 updateExplicit
        verify(mapper, never()).updateExplicit(anyLong(), anyString(), anyLong(), anyLong());
    }

    // ---- 27.2 显式修改、清除与开关 ----

    @Test
    void updateExplicit_sanitizesWhitelistKeysAndCapsValues() throws Exception {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 0), row(1, 1, 0));
        when(mapper.updateExplicit(eq(USER_ID), anyString(), eq(0L), eq(0L))).thenReturn(1);

        Map<String, Object> input = Map.of(
                "liked_styles", List.of("水彩", " ", "水彩", "x".repeat(200)),
                "rogue_key", List.of("不应保存"),
                "notes", "整体偏亮");
        service.updateExplicit(USER_ID, input);

        ArgumentCaptor<String> explicitJson = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateExplicit(eq(USER_ID), explicitJson.capture(), eq(0L), eq(0L));
        Map<String, Object> sanitized = objectMapper.readValue(explicitJson.getValue(), MAP_TYPE);
        assertEquals(List.of("水彩"), sanitized.get("liked_styles"), "重复、空白与超长项必须被过滤");
        assertFalse(sanitized.containsKey("rogue_key"), "白名单外的键不得保存");
        assertEquals("整体偏亮", sanitized.get("notes"));
    }

    @Test
    void updateExplicit_persistentConflict_failsLoudlyInsteadOfSilently() {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(0, 0, 0));
        when(mapper.updateExplicit(eq(USER_ID), anyString(), eq(0L), eq(0L))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.updateExplicit(USER_ID, Map.of("notes", "整体偏亮")));
    }

    @Test
    void clear_wipesAllContentAndKeepsRowAsTombstone() {
        // clear() 路径只读一次：读取的是清除后的行
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(2, 2, 0));

        PreferenceViewVO view = service.clear(USER_ID);

        verify(mapper).clearData(USER_ID);
        assertTrue(view.getExplicit().isEmpty());
        assertTrue(view.getLearned().isEmpty());
        assertTrue(view.getFeedbackHistory().isEmpty());
        assertEquals(2L, view.getPreferenceVersion());
        assertEquals(2L, view.getDataEpoch(), "清除必须递增纪元，挡住在途学习写回");
    }

    @Test
    void setAutoLearn_disabledBumpsEpochSoInFlightLearningDies() {
        when(mapper.selectByUserId(USER_ID)).thenReturn(row(2, 2, 0));

        PreferenceViewVO view = service.setAutoLearn(USER_ID, false);

        verify(mapper).setAutoLearn(USER_ID, 0);
        assertFalse(view.getAutoLearn());
        assertEquals(2L, view.getDataEpoch(), "关闭必须递增纪元，而不只是挡住未来的学习");
    }

    @Test
    void getView_missingRow_returnsDefaults() {
        when(mapper.selectByUserId(USER_ID)).thenReturn(null);

        PreferenceViewVO view = service.getView(USER_ID);

        assertTrue(view.getExplicit().isEmpty());
        assertFalse(view.getAutoLearn(), "自动学习默认必须关闭");
        assertEquals(0L, view.getPreferenceVersion());
    }
}
