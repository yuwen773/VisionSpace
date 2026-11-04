package com.yuwen.visionspace.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.config.AgentConstants;
import com.yuwen.visionspace.agent.model.ActionType;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.UserPreferenceMapper;
import com.yuwen.visionspace.model.dto.agent.PreferenceViewVO;
import com.yuwen.visionspace.model.entity.UserPreference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户长期偏好服务（数据库持久化）。
 *
 * <p>偏好在 {@code user_preference} 表按用户持久保存，跨重启与双实例读取一致。
 * 三块内容分列存储：显式设置（用户直接编辑）、自动学习产物（反馈推断）、
 * 反馈历史；显式设置永远不被学习改写，加载给模型的合并视图里显式键
 * 覆盖同名的推断键——「显式设置优先于模型推断」。
 *
 * <p>并发语义：自动学习只在开关开启后发生，写入按读取时的
 * {@code (preferenceVersion, dataEpoch)} CAS。用户的每一次控制操作
 * （修改/清除/开关）都递增两个值，清除或关闭与在途学习交错时，
 * 旧学习任务的条件更新不再命中——已清除的数据不会被复活，
 * CAS 失败后重读也走不到重放路径（纪元已更替）。
 */
@Slf4j
@Service
public class UserPreferenceService {

    private static final String KEY_LIKED_STYLES = "liked_styles";
    private static final String KEY_DISLIKED_STYLES = "disliked_styles";
    private static final String KEY_LIKED_COLORS = "liked_colors";
    private static final String KEY_NOTES = "notes";

    /** 显式设置白名单：不接受任意键，避免把不受控结构塞进模型上下文 */
    private static final Set<String> EXPLICIT_KEYS =
            Set.of(KEY_LIKED_STYLES, KEY_DISLIKED_STYLES, KEY_LIKED_COLORS, KEY_NOTES);

    private static final int MAX_EXPLICIT_LIST_SIZE = 20;
    private static final int MAX_EXPLICIT_ITEM_LENGTH = 100;
    private static final int MAX_NOTES_LENGTH = 2000;

    private static final String KEY_TIME = "time";
    private static final String KEY_ACTION = "action";
    private static final String KEY_REASON = "reason";

    /** CAS 冲突重试上限；用尽后学习路径只记日志，用户控制路径抛错 */
    private static final int CAS_MAX_RETRIES = 3;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

    private final UserPreferenceMapper userPreferenceMapper;
    private final ObjectMapper objectMapper;

    @Resource
    private AgentConstants agentConstants;

    public UserPreferenceService(UserPreferenceMapper userPreferenceMapper, ObjectMapper objectMapper) {
        this.userPreferenceMapper = userPreferenceMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户反馈（自动学习入口）。
     *
     * <p>只有开关开启后才学习；学习只写 learnedContent 与 feedbackHistory，
     * 不碰 explicitContent。用户清除/关闭与本次学习交错时 CAS 不命中，
     * 已清除的数据不会被旧任务写回。
     */
    public void saveFeedback(Long userId, ActionType action, String reason) {
        if (userId == null) {
            return;
        }
        userPreferenceMapper.insertIfAbsent(userId);

        UserPreference row = userPreferenceMapper.selectByUserId(userId);
        if (row == null || !isAutoLearnOn(row)) {
            // 未开启自动学习：不积累历史，也不做任何推断
            log.debug("自动学习未开启，跳过偏好学习, userId={}", userId);
            return;
        }
        // 学习任务持有开始时的纪元：之后任何用户控制操作（清除/关闭/显式修改）
        // 都递增纪元，重读发现纪元已更替就必须终止——不把旧学习重放成一次新的写入
        long capturedEpoch = row.getDataEpoch();
        for (int attempt = 0; attempt < CAS_MAX_RETRIES; attempt++) {

            Map<String, Object> learned = parseMap(row.getLearnedContent());
            List<Map<String, Object>> history = parseList(row.getFeedbackHistory());

            Map<String, Object> feedback = new HashMap<>();
            feedback.put(KEY_TIME, System.currentTimeMillis());
            feedback.put(KEY_ACTION, action != null ? action.name() : null);
            feedback.put(KEY_REASON, reason);
            history.add(feedback);
            // 限制历史条数，防止无界增长
            int maxSize = agentConstants.getMaxFeedbackHistorySize();
            while (history.size() > maxSize) {
                history.remove(0);
            }

            if (action != null && ActionType.REGENERATE.equals(action) && reason != null) {
                updatePreferencesFromFeedback(learned, reason);
            }

            int updated = userPreferenceMapper.applyLearned(userId,
                    toJson(learned), toJson(history),
                    row.getPreferenceVersion(), row.getDataEpoch());
            if (updated > 0) {
                return;
            }

            // CAS 失败：期间用户清除/关闭或发生过别的写。重读一次再判断——
            // 纪元已更替就终止，不把旧学习重放成一次新的写入
            log.info("偏好学习 CAS 冲突，重读后重试, userId={}, attempt={}", userId, attempt + 1);
            row = userPreferenceMapper.selectByUserId(userId);
            if (row == null || !isAutoLearnOn(row) || row.getDataEpoch() != capturedEpoch) {
                log.info("学习期间用户已清除、关闭或修改偏好，本次学习终止, userId={}", userId);
                return;
            }
        }
        // 多次冲突后放弃：反馈请求本身已成功，学习失败必须可见而不是静默吞掉
        log.warn("偏好学习多次 CAS 冲突，放弃本次学习, userId={}", userId);
    }

    /**
     * 读取偏好视图（用户控制入口）。
     */
    public PreferenceViewVO getView(Long userId) {
        userPreferenceMapper.insertIfAbsent(userId);
        UserPreference row = userPreferenceMapper.selectByUserId(userId);
        return toView(row);
    }

    /**
     * 显式修改偏好设置（用户直接编辑）。
     *
     * <p>白名单外的键被忽略；写入按 (version, epoch) CAS，冲突时重读重试，
     * 用尽后抛错让用户重试——用户输入不被静默丢弃。
     */
    public PreferenceViewVO updateExplicit(Long userId, Map<String, Object> explicit) {
        Map<String, Object> sanitized = sanitizeExplicit(explicit);
        userPreferenceMapper.insertIfAbsent(userId);

        UserPreference row = userPreferenceMapper.selectByUserId(userId);
        for (int attempt = 0; attempt < CAS_MAX_RETRIES; attempt++) {
            if (row == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "偏好记录不存在");
            }
            int updated = userPreferenceMapper.updateExplicit(userId, toJson(sanitized),
                    row.getPreferenceVersion(), row.getDataEpoch());
            if (updated > 0) {
                return toView(userPreferenceMapper.selectByUserId(userId));
            }
            log.info("显式偏好修改 CAS 冲突，重读后重试, userId={}, attempt={}", userId, attempt + 1);
            row = userPreferenceMapper.selectByUserId(userId);
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "偏好修改冲突，请重试");
    }

    /**
     * 清除偏好数据：内容置空、版本与纪元递增，原子完成。
     *
     * <p>不删除行——行是挡住在途学习写回的闸门。自动学习开关保持不变；
     * 开关开着时清除后的新反馈会重新学习，但旧内容不会回来。
     */
    public PreferenceViewVO clear(Long userId) {
        userPreferenceMapper.insertIfAbsent(userId);
        userPreferenceMapper.clearData(userId);
        return toView(userPreferenceMapper.selectByUserId(userId));
    }

    /**
     * 设置自动学习开关。关闭与清除同样递增纪元：
     * 已带着旧版本号在途的学习任务写不进去。
     */
    public PreferenceViewVO setAutoLearn(Long userId, boolean enabled) {
        userPreferenceMapper.insertIfAbsent(userId);
        userPreferenceMapper.setAutoLearn(userId, enabled ? 1 : 0);
        return toView(userPreferenceMapper.selectByUserId(userId));
    }

    /**
     * 加载给模型的合并视图：推断内容打底，显式键覆盖同名键。
     *
     * <p>反馈历史不进入模型上下文（内部核对用），只暴露学习结论与显式设置。
     */
    public Map<String, Object> loadForModel(Long userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        UserPreference row = userPreferenceMapper.selectByUserId(userId);
        if (row == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(parseMap(row.getLearnedContent()));
        // 显式设置优先：同名键覆盖模型推断
        merged.putAll(parseMap(row.getExplicitContent()));
        return merged;
    }

    private PreferenceViewVO toView(UserPreference row) {
        if (row == null) {
            return new PreferenceViewVO(Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList(), false, 0L, 0L);
        }
        return new PreferenceViewVO(
                parseMap(row.getExplicitContent()),
                parseMap(row.getLearnedContent()),
                parseList(row.getFeedbackHistory()),
                isAutoLearnOn(row),
                row.getPreferenceVersion(),
                row.getDataEpoch());
    }

    private boolean isAutoLearnOn(UserPreference row) {
        return row.getAutoLearn() != null && row.getAutoLearn() == 1;
    }

    /**
     * 显式设置白名单过滤与限额：列表键去重限长，notes 截断。
     */
    private Map<String, Object> sanitizeExplicit(Map<String, Object> explicit) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (explicit == null) {
            return result;
        }
        for (String key : EXPLICIT_KEYS) {
            Object value = explicit.get(key);
            if (value == null) {
                continue;
            }
            if (KEY_NOTES.equals(key)) {
                String notes = String.valueOf(value).trim();
                if (!notes.isEmpty()) {
                    result.put(key, notes.length() > MAX_NOTES_LENGTH
                            ? notes.substring(0, MAX_NOTES_LENGTH) : notes);
                }
                continue;
            }
            if (value instanceof List) {
                LinkedHashSet<String> items = new LinkedHashSet<>();
                for (Object item : (List<?>) value) {
                    if (item == null) {
                        continue;
                    }
                    String text = String.valueOf(item).trim();
                    if (text.isEmpty() || text.length() > MAX_EXPLICIT_ITEM_LENGTH) {
                        continue;
                    }
                    items.add(text);
                    if (items.size() >= MAX_EXPLICIT_LIST_SIZE) {
                        break;
                    }
                }
                if (!items.isEmpty()) {
                    result.put(key, new ArrayList<>(items));
                }
            }
        }
        return result;
    }

    /**
     * 从反馈原因里做关键词推断（只改写学习产物，不改显式设置）。
     */
    private void updatePreferencesFromFeedback(Map<String, Object> learned, String reason) {
        List<String> likedStyles = getOrCreateList(learned, KEY_LIKED_STYLES);
        List<String> dislikedStyles = getOrCreateList(learned, KEY_DISLIKED_STYLES);
        List<String> likedColors = getOrCreateList(learned, KEY_LIKED_COLORS);

        // 检测颜色偏好
        if (reason.contains("暗")) {
            dislikedStyles.add("暗色调");
            likedColors.add("亮色系");
        }
        if (reason.contains("亮") || reason.contains("太亮")) {
            dislikedStyles.add("过亮色调");
        }

        // 检测风格偏好
        if (reason.contains("赛博朋克")) {
            likedStyles.add("赛博朋克");
        }
        if (reason.contains("写实")) {
            dislikedStyles.add("过于写实");
        }

        learned.put(KEY_LIKED_STYLES, cap(likedStyles));
        learned.put(KEY_DISLIKED_STYLES, cap(dislikedStyles));
        learned.put(KEY_LIKED_COLORS, cap(likedColors));
    }

    /**
     * 学习产物列表同样限长，防止无界增长。
     */
    private List<String> cap(List<String> items) {
        if (items.size() <= MAX_EXPLICIT_LIST_SIZE) {
            return items;
        }
        return new ArrayList<>(items.subList(items.size() - MAX_EXPLICIT_LIST_SIZE, items.size()));
    }

    @SuppressWarnings("unchecked")
    private List<String> getOrCreateList(Map<String, Object> learned, String key) {
        Object value = learned.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            // 历史数据损坏时不让读取入口 500：按空处理并留下可见日志
            log.warn("偏好内容解析失败，按空处理, error={}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("反馈历史解析失败，按空处理, error={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 序列化失败不能写坏数据：抛出让上层按操作失败处理
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "偏好序列化失败");
        }
    }
}
