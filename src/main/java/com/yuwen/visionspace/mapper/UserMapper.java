package com.yuwen.visionspace.mapper;

import com.yuwen.visionspace.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
* @author 李鱼皮
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2024-12-09 20:03:03
* @Entity com.yuwen.visionspace.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    /**
     * 兑换事务内锁定用户行并读取当前到期时间（#39）。
     * FOR UPDATE 保证同一用户并发兑换不同码时加时串行读改写。
     */
    Date selectVipExpireTimeForUpdate(@Param("userId") Long userId);

    /** 兑换权益更新：只改到期时间，不改角色（#39）。 */
    int updateVipExpireTime(@Param("userId") Long userId, @Param("expireTime") Date expireTime);
}




