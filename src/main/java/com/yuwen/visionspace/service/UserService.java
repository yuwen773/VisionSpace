package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.model.dto.user.UserQueryRequest;
import com.yuwen.visionspace.model.vo.VipExchangeResult;
import com.yuwen.visionspace.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.vo.LoginUserVO;
import com.yuwen.visionspace.model.vo.UserPictureStatsResponse;
import com.yuwen.visionspace.model.vo.UserVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 李鱼皮
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2024-12-09 20:03:03
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户；匿名请求返回 null，而不是抛出未登录异常。
     *
     * <p>用于既允许匿名又允许登录的入口：调用方按 null 走匿名分支，
     * 其余错误照常抛出。
     */
    User getLoginUserIfPresent(HttpServletRequest request);

    /**
     * 获得脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获得脱敏后的用户信息列表
     *
     * @param userList
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 是否具备违规处置权限。该权限独立于平台管理员角色，用于隔离与恢复资源。
     */
    boolean isModerator(User user);

    /**
     * 用户兑换会员（会员码兑换）
     *
     * @return 兑换结果：成功带新到期时间；已用/无效为明确状态，不抛业务异常
     */
    VipExchangeResult exchangeVip(User user, String vipCode);

    /**
     * 获取用户图片统计
     */
    UserPictureStatsResponse getUserPictureStats(User user);
}
