package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.common.SortKeyResolver;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.api.aliyunai.AliYunAiApi;
import com.yuwen.visionspace.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yuwen.visionspace.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.SpaceAuthority;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.dto.picture.*;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PicturePresentationService;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import com.yuwen.visionspace.utils.AfterCommitExecutor;
import com.yuwen.visionspace.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yuwen
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-11 20:45:51
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureStatsService pictureStatsService;






    @Resource
    private PictureContentService pictureContentService;

    // 站内向量检索（RAG）默认关闭；关闭时该 Bean 不存在，调用点统一判空。
    // 用 ObjectProvider 而不是直接注入：PictureEmbeddingServiceImpl 自己依赖
    // PictureService，直接注入会形成循环引用，而 Spring Boot 默认禁止循环引用，
    // 只有开启 RAG 的部署才会在启动期炸掉。
    @Autowired
    private ObjectProvider<PictureEmbeddingService> pictureEmbeddingServiceProvider;




    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceAuthority spaceAuthority;

    @Resource
    private PicturePresentationService picturePresentationService;






    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 内容入口、隔离遮蔽、所有者与统计都由呈现模块统一组装
        return picturePresentationService.present(picture);
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = new Page<>(
                picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        pictureVOPage.setRecords(picturePresentationService.present(picturePage.getRecords()));
        return pictureVOPage;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        SortKeyResolver.Sort sort = SortKeyResolver.resolve(SortKeyResolver.PICTURE_KEYS, sortField, sortOrder);
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
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 校验审核状态是否重复，已是改状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 索引跟随图片当前的审核状态：审核通过则写入索引，被拒绝或撤审则移出索引。
        // 这里只给图片 id，同步时以数据库当前状态为准——用改动前的对象重建索引，
        // 会让一张已转入待审核的公开图片带着旧内容继续留在索引里。
        syncPictureIndex(id);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 业务边界最终复核：注解鉴权依赖请求上下文，直接调用服务时不能跳过归属判断
        checkPictureAuth(loginUser, oldPicture, SpaceUserPermissionConstant.PICTURE_EDIT);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 索引跟随图片编辑后的当前状态：编辑后转入待审核的图片要移出索引，
        // 仍然审核通过的才按新内容重建。同步只给图片 id，不传改动前的对象。
        syncPictureIndex(id);
    }

    /**
     * 触发公开图库索引同步。
     *
     * <p>没有开启站内检索时不存在这个服务，直接跳过。同步在事务提交后执行：
     * 事务回滚时索引不该已经按未提交的状态改过。
     *
     * @param pictureId 图片 id
     */
    private void syncPictureIndex(Long pictureId) {
        PictureEmbeddingService embeddings = embeddingService();
        if (embeddings == null) {
            return;
        }
        AfterCommitExecutor.run(() -> embeddings.syncPictureIndex(pictureId));
    }

    /**
     * 空间写操作的统一判定：所有者完整权限；私有空间仅所有者；团队空间按成员角色。
     *
     * <p>平台管理员不因平台角色获得他人空间的写权限。
     *
     * @param loginUser  当前登录用户
     * @param space      目标空间
     * @param permission 需要的权限码
     */
    private void checkSpaceWriteAuth(User loginUser, Space space, String permission) {
        ThrowUtils.throwIf(!spaceAuthority.canOperateSpace(space, loginUser, permission),
                ErrorCode.NO_AUTH_ERROR);
    }

    @Override
    public Picture getAdminPictureView(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        Picture view = new Picture();
        BeanUtil.copyProperties(picture, view);
        // 管理列表只需要元数据，内容地址一律不返回
        view.setUrl(null);
        view.setThumbnailUrl(null);
        view.setPreviewUrl(null);
        return view;
    }

    /**
     * 图片当前是否处于隔离状态。
     */

    /**
     * 图片写操作的统一判定：公开图库仅作者，私有空间仅所有者，团队空间按成员角色。
     *
     * <p>平台管理员身份不在这里获得私人内容的写权限；违规处置走独立的管理入口。
     *
     * @param loginUser  当前登录用户
     * @param picture    目标图片
     * @param permission 需要的权限码（编辑 / 删除）
     */
    private void checkPictureAuth(User loginUser, Picture picture, String permission) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(!spaceAuthority.canOperatePicture(picture, loginUser, permission),
                ErrorCode.NO_AUTH_ERROR);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        checkSpaceWriteAuth(loginUser, space, SpaceUserPermissionConstant.PICTURE_VIEW);
        // 3. 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 取前 12 个
                .collect(Collectors.toList());
        // 5. 返回结果
        return picturePresentationService.present(sortedPictureList);
    }

    @Override
    public List<PictureVO> getUserRecentPictures(Long userId, int count) {
        if (userId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                .orderByDesc("createTime")
                .last("LIMIT " + count);
        List<Picture> pictures = this.list(queryWrapper);
        if (pictures == null || pictures.isEmpty()) {
            return new ArrayList<>();
        }
        return picturePresentationService.present(pictures);
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        checkSpaceWriteAuth(loginUser, space, SpaceUserPermissionConstant.PICTURE_EDIT);
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 业务边界最终复核：扩图会读取原图并产生外部任务
        checkPictureAuth(loginUser, picture, SpaceUserPermissionConstant.PICTURE_EDIT);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    @Override
    public List<Picture> listApprovedPublicPictures() {
        return this.lambdaQuery()
                .select(Picture::getId, Picture::getUrl, Picture::getThumbnailUrl,
                        Picture::getName, Picture::getIntroduction, Picture::getCategory,
                        Picture::getTags, Picture::getPicColor, Picture::getUserId,
                        Picture::getSpaceId, Picture::getReviewStatus)
                .isNull(Picture::getSpaceId)
                .eq(Picture::getReviewStatus, PictureReviewStatusEnum.PASS.getValue())
                .list();
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 站内向量检索（RAG）未启用时返回 null，调用方一律判空。
     */
    private PictureEmbeddingService embeddingService() {
        return pictureEmbeddingServiceProvider.getIfAvailable();
    }
}










