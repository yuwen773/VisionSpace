package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.AssetSearchSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetSearchSnapshotMapper extends BaseMapper<AssetSearchSnapshot> {
    @Select("SELECT id, ownerId, spaceId, threadId, payload FROM asset_search_snapshot WHERE id = #{id} AND ownerId = #{ownerId}")
    AssetSearchSnapshot selectOwned(@Param("id") String id, @Param("ownerId") Long ownerId);
}
