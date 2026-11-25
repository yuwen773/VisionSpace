package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.common.SortKeyResolver;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.auth.StpKit;
import com.yuwen.visionspace.manager.auth.TokenAuthenticationSupport;
import com.yuwen.visionspace.model.dto.user.UserQueryRequest;
import com.yuwen.visionspace.model.vo.VipExchangeResult;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.UserRoleEnum;
import com.yuwen.visionspace.model.vo.LoginUserVO;
import com.yuwen.visionspace.model.vo.UserPictureStatsResponse;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.PasswordHasher;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.UserService;
import com.yuwen.visionspace.service.VipRedemptionService;
import com.yuwen.visionspace.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.dev33.satoken.stp.parameter.SaLogoutParameter;
import cn.dev33.satoken.stp.parameter.enums.SaLogoutMode;
import cn.dev33.satoken.stp.parameter.enums.SaLogoutRange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 李鱼皮
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2024-12-09 20:03:03
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TokenAuthenticationSupport tokenAuthenticationSupport;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查用户账号是否和数据库中已有的重复
        // 账号唯一索引建在 lower(user_account) 上（大小写不敏感），查重必须用同一表达式，
        // 否则大小写不同的账号会绕过这里、最后由数据库唯一索引抛出 500。
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.apply("lower(user_account) = lower({0})", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 密码使用每用户独立盐的自适应摘要
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        User user = authenticateForPassword(userAccount, userPassword);
        // 保存用户的登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public User authenticateForPassword(String userAccount, String userPassword) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }
        // 2. 按账号取回摘要：摘要带独立盐，不能再用「账号 + 摘要」拼查询条件
        // 账号按大小写不敏感匹配（与 lower(user_account) 唯一索引一致）
        User user = this.lambdaQuery()
                .apply("lower(user_account) = lower({0})", userAccount)
                .one();
        // 不存在，抛异常。未命中时照样走一次哈希校验，避免以响应时间枚举账号
        if (user == null || !passwordHasher.matches(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount cannot match userPassword");
            if (user == null) {
                passwordHasher.matches(userPassword, passwordHasher.hash("timing-equalizer"));
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 3. 旧摘要或低成本摘要：仅在验证成功后升级。失败路径不写库。
        upgradeStoredPassword(user, userPassword);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(long id) {
        // 先释放微信绑定：逻辑删除只改 isDelete，行还在，唯一索引仍占着这个 openid。
        // 不清空的话，该微信下次登录查不到已删账号（40102），
        // 换绑新账号又撞唯一索引（40103），两头都走不通。
        // 两步必须同一事务：解绑成功而删除失败会留下「账号还在、绑定没了」的中间态。
        // 解绑的 UPDATE 会锁住该行，并发绑定只能在它前后排队，不会插进两步之间。
        this.lambdaUpdate()
                .set(User::getWxOpenId, null)
                .eq(User::getId, id)
                .update();
        return this.removeById(id);
    }

    /**
     * 登录成功后把旧摘要升级为当前算法。
     *
     * <p>并发登录可能同时升级：更新条件是「摘要仍是本实例读到的那一个」，
     * 因此只有第一个写入生效，后到者影响 0 行。升级只改 {@code userPassword}，
     * 不触碰角色、会员等其它列，回滚只需保留旧摘要的备份数据。
     */
    private void upgradeStoredPassword(User user, String rawPassword) {
        String stored = user.getUserPassword();
        if (!passwordHasher.needsUpgrade(stored)) {
            return;
        }
        String upgraded = passwordHasher.hash(rawPassword);
        try {
            boolean updated = this.lambdaUpdate()
                    .set(User::getUserPassword, upgraded)
                    .eq(User::getId, user.getId())
                    .eq(User::getUserPassword, stored)
                    .update();
            if (updated) {
                user.setUserPassword(upgraded);
                log.info("password digest upgraded, userId={}", user.getId());
            }
        } catch (Exception e) {
            // 升级失败不影响本次登录：用户仍可用原摘要继续登录。
            // 调用链（authenticateForPassword 及其两个调用方）都不在事务里，
            // 单条 UPDATE 失败只会回滚这一条语句，连接不会停留在 aborted 状态；
            // 若将来把这里包进事务，必须改成独立事务（否则后续语句会得到 25P02）。
            log.warn("password digest upgrade failed, userId={}", user.getId(), e);
        }
    }

    /**
     * 生成密码摘要
     *
     * @param userPassword 用户密码
     * @return 带独立盐的自适应摘要
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        return passwordHasher.hash(userPassword);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 小程序等非浏览器客户端：请求头携带令牌，不依赖 Cookie 会话。
        // 先判令牌再判会话，令牌客户端因此不会顺带创建空 Session。
        Long tokenUserId = tokenAuthenticationSupport.resolveUserId(request);
        if (tokenUserId != null) {
            User byToken = this.getById(tokenUserId);
            if (byToken == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return byToken;
        }
        // 判断是否已经登录
        HttpSession session = request.getSession(false);
        Object userObj = session == null ? null : session.getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库中查询（追求性能的话可以注释，直接返回上述结果）
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public User getLoginUserIfPresent(HttpServletRequest request) {
        try {
            return this.getLoginUser(request);
        } catch (BusinessException e) {
            if (ErrorCode.NOT_LOGIN_ERROR.getCode() == e.getCode()) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 获取脱敏类的用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userObj = session == null ? null : session.getAttribute(UserConstant.USER_LOGIN_STATE);
        boolean spaceLoggedIn = StpKit.SPACE.isLogin();
        // 小程序用请求头带令牌，Sa-Token 自己也会读到同一个头，无法据此区分两端，
        // 所以这里显式解析一次，判断「本次请求是不是令牌客户端」。
        String explicitToken = tokenAuthenticationSupport.resolveTokenValue(request);
        boolean tokenLoggedIn = explicitToken != null && tokenAuthenticationSupport.resolveUserId(request) != null;
        if (userObj == null && !spaceLoggedIn && !tokenLoggedIn) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 1. 撤销认证。
        //    * 令牌客户端（无浏览器会话、显式带头）：只撤销本次令牌，一台设备退出
        //      不会把同账号其它设备一起踢下线；
        //    * 浏览器：按账号撤销，连带清掉 Sa-Token Cookie 对应的令牌与其它端令牌，
        //      保留「Web 退出即全端退出」的既有语义。
        // 撤销失败必须让调用方看到，不能吞掉异常后返回「已注销」：
        // 那样会话被清掉、令牌却还活着，用户以为退出了而空间鉴权仍然通过。
        if (tokenLoggedIn && userObj == null) {
            StpKit.SPACE.logoutByTokenValue(explicitToken);
        } else if (spaceLoggedIn) {
            StpKit.SPACE.logout(SaLogoutParameter.create()
                    .setMode(SaLogoutMode.LOGOUT)
                    .setRange(SaLogoutRange.ACCOUNT));
        }
        // 2. 失效整个 HTTP 会话，只移除单个属性会让旧 Session 继续可用
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException e) {
                log.debug("http session already invalidated");
            }
        }
        return true;
    }

    @Override
    public List<UserVO> searchForAdmin(String keyword) {
        String kw = StrUtil.trim(keyword);
        if (StrUtil.isBlank(kw)) {
            return new ArrayList<>();
        }
        // 姓名与账号任一命中即可：操作员手里可能只有其中一项
        String like = "%" + kw + "%";
        List<User> users = lambdaQuery()
                .and(w -> w.apply("user_name ILIKE {0}", like).or().apply("user_account ILIKE {0}", like))
                .orderByDesc(User::getCreateTime)
                .last("LIMIT 20")
                .list();
        return getUserVOList(users);
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "user_role", userRole);
        queryWrapper.apply(StrUtil.isNotBlank(userAccount), "user_account ILIKE {0}", "%" + userAccount + "%");
        queryWrapper.apply(StrUtil.isNotBlank(userName), "user_name ILIKE {0}", "%" + userName + "%");
        queryWrapper.apply(StrUtil.isNotBlank(userProfile), "user_profile ILIKE {0}", "%" + userProfile + "%");
        SortKeyResolver.Sort sort = SortKeyResolver.resolve(SortKeyResolver.USER_KEYS, sortField, sortOrder);
        if (!sort.isEmpty()) {
            if (sort.ascending()) {
                queryWrapper.orderByAsc(sort.column());
            } else {
                queryWrapper.orderByDesc(sort.column());
            }
        }
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean isModerator(User user) {
        // 处置权限只来自独立角色；平台管理员与资源所有者都不自动获得
        return user != null && UserRoleEnum.MODERATOR.getValue().equals(user.getUserRole());
    }

    // region ------- 以下代码为用户兑换会员功能 --------

    @jakarta.annotation.Resource
    @Lazy
    private PictureService pictureService;

    @jakarta.annotation.Resource
    @Lazy
    private VipRedemptionService vipRedemptionService;

    /**
     * 兑换会员（#39）：核销、权益与流水在 VipRedemptionService 的同一事务内完成。
     * 结果是明确状态（成功/已用/无效），不再依赖异常文案；不触碰 userRole。
     *
     * @param user    服务端确认的登录用户
     * @param vipCode 用户提交的明文兑换码
     * @return 兑换结果
     */
    @Override
    public VipExchangeResult exchangeVip(User user, String vipCode) {
        if (user == null || StrUtil.isBlank(vipCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 核销服务已在事务内完成权益更新并带上新到期时间，无需回读
        return vipRedemptionService.redeem(user, vipCode);
    }

    // endregion ------- 以下代码为用户兑换会员功能 --------

    @Override
    public UserPictureStatsResponse getUserPictureStats(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        UserPictureStatsResponse stats = new UserPictureStatsResponse();
        // 查询该用户上传的图片总数
        Long uploadCount = pictureService.count(new QueryWrapper<Picture>()
            .eq("user_id", user.getId()));
        stats.setUploadCount(uploadCount);

        // 查询审核通过数量
        Long reviewPassCount = pictureService.count(new QueryWrapper<Picture>()
            .eq("user_id", user.getId())
            .eq("review_status", 1)); // 1=通过
        stats.setReviewPassCount(reviewPassCount);

        // 计算通过率
        if (uploadCount > 0) {
            double rate = reviewPassCount * 100.0 / uploadCount;
            stats.setReviewPassRate(Math.round(rate * 100) / 100.0);
        } else {
            stats.setReviewPassRate(0.0);
        }

        // 收藏数量暂无实现，预留
        stats.setLikeCount(0L);

        return stats;
    }
}




