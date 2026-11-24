package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.Picture;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author 李鱼皮
 * @description 针对表【picture(图片)】的数据库操作Mapper
 * @createDate 2024-12-11 20:45:51
 * @Entity com.yuwen.visionspace.model.entity.Picture
 */
@Mapper
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 按互动量取有界候选：公开图库里已审核通过、且不属于任何空间的图片。
     *
     * <p>候选池必须有界：热门排序只在候选池内评分，候选池之外的数据不参与计算。
     *
     * @param limit 最多返回的候选数
     * @return 候选图片 id（按互动量降序）
     */
    List<Long> selectEngagementCandidateIds(@Param("limit") int limit);

    /**
     * 按创建时间取有界候选：用于给热门候选池补入新内容。
     *
     * @param limit 最多返回的候选数
     * @return 候选图片 id（按创建时间降序）
     */
    List<Long> selectRecentCandidateIds(@Param("limit") int limit);

    /**
     * 按确定性随机序取有界候选。
     *
     * <p>同一个种子对同一份数据给出同一个顺序：翻页时不会重复也不会漏掉。
     *
     * @param seed 随机上下文种子
     * @param limit 最多返回的候选数
     * @return 候选图片 id
     */
    List<Long> selectRandomCandidateIds(@Param("seed") String seed, @Param("limit") int limit);

    /**
     * 统计公开推荐范围内、符合质量条件的图片数。
     *
     * @param minPixels 最小像素数
     * @return 图片数
     */
    long countQualityCandidates(@Param("minPixels") long minPixels);

    /**
     * 统计公开推荐范围内的图片数。
     *
     * @return 图片数
     */
    long countPublicCandidates();

    /**
     * 锁定图片行：SELECT ... FOR UPDATE。
     *
     * <p>隔离、替换、删除都先取这把行锁，破坏性动作按同一顺序串行化。
     * 行锁由数据库提供，跨实例有效；锁在当前事务内一直持有到提交或回滚。
     *
     * <p>与业务查询一致地排除逻辑删除的记录：图片被删除后不再是可以被处置的目标。
     *
     * @param id 图片标识
     * @return 图片存在时返回其标识，不存在时返回 null
     */
    @Select("SELECT id FROM picture WHERE id = #{id} AND is_delete = 0 FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);

    /**
     * 锁定并读出图片当前行。
     *
     * <p>替换路径要按「新旧大小差额」调整空间额度：差额必须基于锁内读到的最新
     * 大小，而不是请求开始前读到的值。两个实例先后替换同一张图时，后到的一方
     * 在拿到行锁后才读，看到的是前一方已经提交的大小。
     *
     * @param id 图片标识
     * @return 图片行；已删除或不存在时返回 null
     */
    @Select("SELECT * FROM picture WHERE id = #{id} AND is_delete = 0 FOR UPDATE")
    Picture selectForUpdate(@Param("id") Long id);

    /**
     * 公开图库里当前不该被检索的图片 id：审核未通过（待审核、拒绝或状态为空）。
     *
     * <p>这类图片可能还留在向量库里：审核撤销只改了数据库，异步删除索引可能
     * 还没执行。全量同步按这个清单把它们移出索引，索引才会与数据库一致。
     *
     * @param pass 审核通过的状态值
     * @return 图片 id 列表
     */
    @Select("""
            SELECT id FROM picture
            WHERE space_id IS NULL AND is_delete = 0 AND (review_status IS NULL OR review_status <> #{pass})
            """)
    List<Long> selectNonApprovedPublicPictureIds(@Param("pass") int pass);

    /**
     * 公开图库里审核通过但正处于隔离状态的图片 id。
     *
     * <p>隔离中的图片不对外展示，索引里也不保留；恢复后由隔离流程重新触发同步。
     *
     * @param pass         审核通过的状态值
     * @param resourceType 资源类型标识
     * @param quarantined  隔离中的状态值
     * @return 图片 id 列表
     */
    @Select("""
            SELECT p.id FROM picture p
            JOIN resource_isolation ri
              ON ri.resource_id = p.id
             AND ri.resource_type = #{resourceType}
             AND ri.status = #{quarantined}
            WHERE p.space_id IS NULL AND p.is_delete = 0 AND p.review_status = #{pass}
            """)
    List<Long> selectQuarantinedApprovedPublicPictureIds(@Param("pass") int pass,
                                                         @Param("resourceType") String resourceType,
                                                         @Param("quarantined") int quarantined);
}
