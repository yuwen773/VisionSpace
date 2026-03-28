package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户图片行为 Mapper
 * 继承 MyBatis Plus 的 BaseMapper，获得基本的 CRUD 能力
 *
 * @see UserPictureAction 用户图片行为实体
 */
@Mapper
public interface UserPictureActionMapper extends BaseMapper<UserPictureAction> {

    /**
     * 按图片ID分组统计各行为数量
     * @param pictureIds 图片ID列表
     * @return 聚合统计列表
     */
    @Select("<script>" +
            "SELECT " +
            "picture_id AS pictureId, " +
            "SUM(CASE WHEN action_type = 0 THEN action_value ELSE 0 END) AS impressionCount, " +
            "SUM(CASE WHEN action_type = 1 THEN action_value ELSE 0 END) AS clickCount, " +
            "SUM(CASE WHEN action_type = 2 THEN action_value ELSE 0 END) AS viewCount, " +
            "SUM(CASE WHEN action_type = 3 THEN action_value ELSE 0 END) AS likeCount, " +
            "SUM(CASE WHEN action_type = 4 THEN action_value ELSE 0 END) AS collectCount, " +
            "SUM(CASE WHEN action_type = 5 THEN action_value ELSE 0 END) AS downloadCount, " +
            "SUM(CASE WHEN action_type = 6 THEN action_value ELSE 0 END) AS shareCount " +
            "FROM user_picture_action " +
            "WHERE picture_id IN " +
            "<foreach item='item' collection='list' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "GROUP BY picture_id" +
            "</script>")
    List<PictureActionStatsDTO> aggregateByPictureId(List<Long> pictureIds);

    /**
     * 查询有行为记录的图片ID列表
     * @return 图片ID列表
     */
    @Select("SELECT DISTINCT picture_id FROM user_picture_action")
    List<Long> selectPictureIdsWithActions();
}
