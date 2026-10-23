package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.Picture;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
