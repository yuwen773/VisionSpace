package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.model.dto.spaceuser.SpaceUserAddRequest;
import com.yuwen.visionspace.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.vo.SpaceUserVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 李鱼皮
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2025-01-02 20:07:15
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest 成员信息
     * @param operator            当前登录用户，用于复核成员管理权限
     * @return 新成员记录 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, User operator);

    /**
     * 校验成员管理权限。
     *
     * <p>仅空间所有者或该空间的团队管理员可管理成员；拒绝时抛业务异常。
     *
     * @param operator 当前登录用户
     * @param spaceId  目标空间 id
     */
    void checkManageAuth(User operator, Long spaceId);

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add       是否为创建时检验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
