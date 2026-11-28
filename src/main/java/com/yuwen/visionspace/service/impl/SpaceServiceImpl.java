package com.yuwen.visionspace.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.common.SortKeyResolver;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.SpaceAuthority;
import com.yuwen.visionspace.model.dto.space.SpaceAddRequest;
import com.yuwen.visionspace.model.dto.space.SpaceQueryRequest;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceLevelEnum;
import com.yuwen.visionspace.model.enums.SpaceRoleEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.model.vo.SpaceVO;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 李鱼皮
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2024-12-18 19:53:34
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceAuthority spaceAuthority;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 创建空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        return addSpaceForOwner(spaceAddRequest, loginUser, loginUser.getId());
    }

    /**
     * 创建空间并指定归属用户。
     *
     * <p>普通用户只能给自己建空间；管理员可以在管理端代某个用户建空间。
     * 权限判断始终以操作者（operator）的身份进行，归属用户（ownerUserId）只决定
     * 空间记在谁名下，二者不能混用，否则普通用户就能伪造 userId 越权建空间。
     */
    @Override
    public long addSpaceForOwner(SpaceAddRequest spaceAddRequest, User operator, Long ownerUserId) {
        ThrowUtils.throwIf(operator == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(ownerUserId == null || ownerUserId <= 0, ErrorCode.PARAMS_ERROR, "归属用户不能为空");
        if (!ownerUserId.equals(operator.getId()) && !userService.isAdmin(operator)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限为其他用户创建空间");
        }
        User owner = userService.getById(ownerUserId);
        ThrowUtils.throwIf(owner == null, ErrorCode.NOT_FOUND_ERROR, "归属用户不存在");

        // 1. 填充参数默认值
        // 转换实体类和 DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        this.validSpace(space, true);
        // 3. 校验权限，非管理员只能创建普通级别的空间
        space.setUserId(ownerUserId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(operator)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 4. 同一用户每种类型最多一个有效空间。
        //    唯一性由数据库的 activeKey 唯一索引保证，而不是进程内锁：
        //    进程内锁只在单个 JVM 内生效，两个实例同时创建时都会看到"不存在"。
        //    冲突时唯一索引让后提交的一方失败，事务整体回滚，不会留下半条记录。
        space.setActiveKey(activeKeyOf(ownerUserId, space.getSpaceType()));
        try {
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                // 创建成功后，如果是团队空间，关联新增团队成员记录。
                // 成员记录与空间在同一事务内提交：任何一步失败都不会留下无主空间。
                if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(ownerUserId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 返回新写入的数据 id
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突是明确的业务结果：同类空间已经存在，而不是系统错误
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
        }
    }

    /**
     * 有效空间唯一占位键：同一创建者的同一空间类型只允许一条有效记录。
     */
    private static String activeKeyOf(Long userId, Integer spaceType) {
        return userId + ":" + spaceType;
    }

    /**
     * 删除空间：逻辑删除并释放唯一占位，之后可以重新创建。
     *
     * <p>两步在同一条语句里完成：只删不释放会让该用户此后永远无法再建同类空间；
     * 只释放不删会留下两条有效记录。条件里的 {@code isDelete = 0} 让重复删除
     * 只影响 0 行，不会覆盖别的空间。
     *
     * @return 是否删除了这条空间（已删除或不存在时返回 false）
     */
    @Override
    public boolean deleteSpace(Long spaceId) {
        if (spaceId == null || spaceId <= 0) {
            return false;
        }
        return baseMapper.softDeleteAndReleaseActiveKey(spaceId) > 0;
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 创建时校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不能为空");
            }
        }
        // 修改数据时，空间名称进行校验
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // 修改数据时，空间级别进行校验
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        // 修改数据时，空间类别进行校验
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不存在");
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public Page<Space> pageAccessible(Page<Space> page, QueryWrapper<Space> queryWrapper, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        if (userService.isAdmin(loginUser)) {
            return this.page(page, queryWrapper);
        }
        // 本人创建的空间 + 已加入的团队空间
        List<Long> joinedSpaceIds = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getUserId, loginUser.getId())
                .list()
                .stream()
                .map(SpaceUser::getSpaceId)
                .toList();
        queryWrapper.and(wrapper -> {
            wrapper.eq("user_id", loginUser.getId());
            if (!joinedSpaceIds.isEmpty()) {
                wrapper.or().in("id", joinedSpaceIds);
            }
        });
        return this.page(page, queryWrapper);
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        Integer status = spaceQueryRequest.getStatus();
        String userKeyword = spaceQueryRequest.getUserKeyword();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "user_id", userId);
        queryWrapper.apply(StrUtil.isNotBlank(spaceName), "space_name ILIKE {0}", "%" + spaceName + "%");
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "space_level", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "space_type", spaceType);
        queryWrapper.eq(ObjUtil.isNotEmpty(status), "status", status);
        // 所有者关键字：先按用户名/账号解析出用户 id，再收敛空间范围。
        // 解析不到用户时用恒假条件返回空页，而不是忽略关键字返回全部空间。
        if (StrUtil.isNotBlank(userKeyword)) {
            String kw = "%" + userKeyword + "%";
            List<Long> ownerIds = userService.lambdaQuery()
                    .and(w -> w.apply("user_name ILIKE {0}", kw).or().apply("user_account ILIKE {0}", kw))
                    .list()
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            if (ownerIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in("user_id", ownerIds);
            }
        }
        // 排序
        SortKeyResolver.Sort sort = SortKeyResolver.resolve(SortKeyResolver.SPACE_KEYS, sortField, sortOrder);
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
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 空间元数据属于平台管理范围：所有者与平台管理员都可以编辑。
        // 私人内容的读取与写操作另由 SpaceAuthority 判定，不在这里放行。
        ThrowUtils.throwIf(!spaceAuthority.canEditSpaceMetadata(space, loginUser),
                ErrorCode.NO_AUTH_ERROR);
    }
}


