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
    @Select("SELECT `id` FROM `picture` WHERE `id` = #{id} AND `isDelete` = 0 FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);
}
