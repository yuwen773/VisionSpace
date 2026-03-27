package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户图片行为 Mapper
 * 继承 MyBatis Plus 的 BaseMapper，获得基本的 CRUD 能力
 *
 * @param <T> 实体类型
 * @see UserPictureAction 用户图片行为实体
 */
@Mapper
public interface UserPictureActionMapper extends BaseMapper<UserPictureAction> {
}
