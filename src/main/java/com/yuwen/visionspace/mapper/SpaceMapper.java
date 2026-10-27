package com.yuwen.visionspace.mapper;

import com.yuwen.visionspace.model.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
* @author 李鱼皮
* @description 针对表【space(空间)】的数据库操作Mapper
* @createDate 2024-12-18 19:53:34
* @Entity com.yuwen.visionspace.model.entity.Space
*/
@Mapper
public interface SpaceMapper extends BaseMapper<Space> {

    /**
     * 占用一个图片名额：数量 +1、大小 +本次字节数。
     *
     * <p>判定与写入是同一条语句，条件在数据库里求值：两个实例同时上传时，
     * 后到的一方看到的是先到者已经提交的计数，不可能同时通过上限判定。
     * 超出上限时影响 0 行，计数保持原样。
     *
     * @param spaceId 空间 id
     * @param size    本次新增的字节数
     * @return 影响行数；0 表示空间不存在或已达上限
     */
    @Update("UPDATE `space` SET `totalCount` = `totalCount` + 1, `totalSize` = `totalSize` + #{size}, " +
            "`updateTime` = NOW() " +
            "WHERE `id` = #{spaceId} AND `isDelete` = 0 " +
            "AND `totalCount` < `maxCount` AND `totalSize` + #{size} <= `maxSize`")
    int reservePictureSlot(@Param("spaceId") Long spaceId, @Param("size") long size);

    /**
     * 按新旧大小差额调整已用大小，数量不变。
     *
     * <p>差额为正时同时判定上限；差额为负（替换成更小的图）不受上限限制。
     * {@code GREATEST(..., 0)} 保证已用大小不会变成负数。
     *
     * @param spaceId 空间 id
     * @param delta   新图字节数减去旧图字节数，可为负
     * @return 影响行数；0 表示空间不存在或增大后超出上限
     */
    @Update("UPDATE `space` SET `totalSize` = GREATEST(`totalSize` + #{delta}, 0), `updateTime` = NOW() " +
            "WHERE `id` = #{spaceId} AND `isDelete` = 0 " +
            "AND (#{delta} <= 0 OR `totalSize` + #{delta} <= `maxSize`)")
    int applyPictureSizeDelta(@Param("spaceId") Long spaceId, @Param("delta") long delta);

    /**
     * 释放一个图片名额：数量 -1、大小 -本次字节数。
     *
     * <p>用 {@code GREATEST(..., 0)} 收敛到 0：存量计数本身可能已经偏高或偏低，
     * 删除不能被历史误差卡住，也不能让计数变成负数。偏差由维护清单核对。
     *
     * @param spaceId 空间 id
     * @param size    被删除图片的字节数
     * @return 影响行数；0 表示空间不存在
     */
    @Update("UPDATE `space` SET `totalCount` = GREATEST(`totalCount` - 1, 0), " +
            "`totalSize` = GREATEST(`totalSize` - #{size}, 0), `updateTime` = NOW() " +
            "WHERE `id` = #{spaceId}")
    int releasePictureSlot(@Param("spaceId") Long spaceId, @Param("size") long size);

    /**
     * 逻辑删除空间并释放有效空间唯一占位。
     *
     * <p>同一条语句完成两件事：释放 activeKey 让同一创建者可以再建同类空间，
     * 同时保留 isDelete=1 的历史记录。条件带 {@code isDelete = 0}，重复删除
     * 影响 0 行，不会误改已经删除的空间。
     *
     * @param spaceId 空间 id
     * @return 影响行数
     */
    @Update("UPDATE `space` SET `isDelete` = 1, `activeKey` = NULL, `updateTime` = NOW() " +
            "WHERE `id` = #{spaceId} AND `isDelete` = 0")
    int softDeleteAndReleaseActiveKey(@Param("spaceId") Long spaceId);
}
