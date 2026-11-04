package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.UserPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户长期偏好的持久化入口。
 *
 * <p>与 {@code AgentRunStateMapper} 同一套并发纪律：归属条件写进每条 SQL，
 * 跨用户读写在这里就不可能发生；跨实例的状态变化用数据库原子条件更新表达，
 * 不做「先读后写」。
 *
 * <p>学习写入的双重 CAS：{@code preferenceVersion} 拦住「读到之后用户改过」，
 * {@code dataEpoch} 拦住「CAS 失败后重读再写」的重放路径——清除或关闭后
 * 纪元已经更替，旧学习任务无论重试多少次都写不进已清除的数据。
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {

    /**
     * 首次为用户建立偏好行。已存在时是空操作，绝不改写归属与已有内容。
     *
     * @return 1 表示本次创建，0 表示已存在
     */
    @Insert("INSERT INTO user_preference (userId, autoLearn, preferenceVersion, dataEpoch, " +
            "createTime, updateTime) VALUES (#{userId}, 0, 0, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE userId = userId")
    int insertIfAbsent(@Param("userId") Long userId);

    /**
     * 按用户读取偏好行。归属条件写进查询，读到别人的偏好在这里就不可能发生。
     */
    @Select("SELECT * FROM user_preference WHERE userId = #{userId}")
    UserPreference selectByUserId(@Param("userId") Long userId);

    /**
     * 显式设置写入：只有版本与纪元都还是读取时的值才生效。
     *
     * <p>用户控制操作与在途学习之间的顺序由此保证——学习写入与显式写入都按
     * (version, epoch) CAS，后提交者要求「期间没有别的写」，先提交者的结果
     * 不会被静默覆盖。冲突时由调用方重读重试。
     *
     * @return 1 表示已写入，0 表示期间发生过别的写（版本/纪元已更替）
     */
    @Update("UPDATE user_preference SET explicitContent = #{explicitJson}, " +
            "preferenceVersion = preferenceVersion + 1, dataEpoch = dataEpoch + 1, updateTime = NOW() " +
            "WHERE userId = #{userId} AND preferenceVersion = #{expectedVersion} AND dataEpoch = #{expectedEpoch}")
    int updateExplicit(@Param("userId") Long userId,
                       @Param("explicitJson") String explicitJson,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("expectedEpoch") long expectedEpoch);

    /**
     * 自动学习写入：版本与纪元 CAS 之外还要求开关仍为开启。
     *
     * <p>这是「旧学习任务不能复活已清除数据」的第一道闸：用户清除/关闭与在途
     * 学习交错时，清除/关闭已递增版本与纪元，本条件不成立，影响 0 行。
     * 只写 learnedContent 与 feedbackHistory，explicitContent 永远不被学习改写。
     *
     * @return 1 表示已写入，0 表示已清除、已关闭或期间发生过别的写
     */
    @Update("UPDATE user_preference SET learnedContent = #{learnedJson}, " +
            "feedbackHistory = #{feedbackHistoryJson}, " +
            "preferenceVersion = preferenceVersion + 1, updateTime = NOW() " +
            "WHERE userId = #{userId} AND preferenceVersion = #{expectedVersion} " +
            "AND dataEpoch = #{expectedEpoch} AND autoLearn = 1")
    int applyLearned(@Param("userId") Long userId,
                     @Param("learnedJson") String learnedJson,
                     @Param("feedbackHistoryJson") String feedbackHistoryJson,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("expectedEpoch") long expectedEpoch);

    /**
     * 清除偏好数据：内容置空、版本与纪元递增，一行原子完成。
     *
     * <p>不删除行：行是墓碑也是闸门——删除行会让在途学习任务随后
     * {@link #insertIfAbsent} 重建出旧内容看似存在的假象；置空内容 + 递增纪元
     * 让旧任务的条件更新永远不再命中。自动学习开关保持不变（清除数据 ≠ 关闭学习）。
     *
     * @return 1 表示已清除，0 表示用户本就没有偏好行（视为已清除）
     */
    @Update("UPDATE user_preference SET explicitContent = NULL, learnedContent = NULL, " +
            "feedbackHistory = NULL, preferenceVersion = preferenceVersion + 1, " +
            "dataEpoch = dataEpoch + 1, updateTime = NOW() " +
            "WHERE userId = #{userId}")
    int clearData(@Param("userId") Long userId);

    /**
     * 设置自动学习开关：版本与纪元递增。
     *
     * <p>关闭与清除同样递增纪元：关闭不能只是挡住「未来的学习」，还要让
     * 已经带着旧版本号在途的学习任务写不进去。
     *
     * @return 1 表示已写入，0 表示用户本就没有偏好行
     */
    @Update("UPDATE user_preference SET autoLearn = #{enabled}, " +
            "preferenceVersion = preferenceVersion + 1, dataEpoch = dataEpoch + 1, updateTime = NOW() " +
            "WHERE userId = #{userId}")
    int setAutoLearn(@Param("userId") Long userId, @Param("enabled") int enabled);
}
