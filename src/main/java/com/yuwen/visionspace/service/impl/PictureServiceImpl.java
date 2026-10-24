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
import com.yuwen.visionspace.manager.auth.SpaceUserAuthManager;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.manager.lock.ResourceLockManager;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.manager.storage.UploadQuotaManager;
import com.yuwen.visionspace.manager.upload.FilePictureUpload;
import com.yuwen.visionspace.manager.upload.PictureUploadTemplate;
import com.yuwen.visionspace.manager.upload.UrlPictureUpload;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.model.dto.picture.*;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PictureFileService;
import com.yuwen.visionspace.service.PicturePreviewService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import com.yuwen.visionspace.utils.ColorSimilarUtils;
import com.yuwen.visionspace.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
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
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private PicturePreviewService picturePreviewService;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureFileService pictureFileService;

    @Resource
    private PictureContentService pictureContentService;

    @Resource
    private UploadQuotaManager uploadQuotaManager;


    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Resource
    private ResourceLockManager resourceLockManager;

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
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 业务边界复核：只有空间所有者或团队编辑者可向该空间上传
            checkSpaceWriteAuth(loginUser, space, SpaceUserPermissionConstant.PICTURE_UPLOAD);
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 重新上传会覆盖原图，业务边界必须复核归属
            checkPictureAuth(loginUser, oldPicture);
            // 预检：请求开始前就已隔离的资源直接拒绝，不进入对象存储
            // 事务内还有一次权威判定（行锁 + 锁定读），覆盖预检之后隔离才生效的竞态
            ThrowUtils.throwIf(isQuarantined(pictureId), ErrorCode.OPERATION_ERROR, "资源处于隔离状态，不能替换");
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("/public/%s/", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("/space/%s/", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        // 用户级速率与额度：超限时在进入对象存储前拒绝，拒绝路径零副作用
        uploadQuotaManager.checkRate(loginUser.getId(), PictureFilePurposeEnum.GALLERY);
        // 上传前的预检只能看已用额度；本次文件大小要等上传完成后才知道
        uploadQuotaManager.checkQuota(loginUser.getId(), null);
        UploadPictureResult uploadPictureResult;
        try {
            uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        } catch (RuntimeException e) {
            // 存储失败不计入当日上传次数
            uploadQuotaManager.refundRate(loginUser.getId(), PictureFilePurposeEnum.GALLERY);
            throw e;
        }
        try {
            // 把本次上传的字节数计入额度，避免"已用未超、再加就超"的请求落库
            uploadQuotaManager.checkQuota(loginUser.getId(), uploadPictureResult.getPicSize());
        } catch (RuntimeException e) {
            discardUploadedObjects(loginUser.getId(), uploadPictureResult);
            throw e;
        }
        // 登记资源归属：所有者、平台与对象键都来自服务端，后续删除/读取以记录为准
        List<PictureFile> uploadedFiles;
        try {
            uploadedFiles = pictureFileService.registerUpload(
                    loginUser, PictureFilePurposeEnum.GALLERY, spaceId, uploadPictureResult);
        } catch (RuntimeException e) {
            // 记录登记失败时对象已经写入存储：先按平台与对象键回收，再让请求失败
            discardUploadedObjects(loginUser.getId(), uploadPictureResult);
            throw e;
        }
        // 构造要入库的图片信息
        Picture picture = new Picture();
        // 属性拷贝
        BeanUtil.copyProperties(uploadPictureResult, picture);
        // 转换为标准颜色（6位十六进制）
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setSpaceId(spaceId);
        picture.setPreviewUrl(null);
        // 支持外层传递图片名称
        String picName = StrUtil.isNotBlank(pictureUploadRequest.getPicName()) ? pictureUploadRequest.getPicName()
                : uploadPictureResult.getPicName();
        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        Long finalPictureId = pictureId;
        try {
            transactionTemplate.execute(status -> {
                if (finalPictureId != null) {
                    // 替换已有资源：先取资源行锁，再按最新状态复核隔离。
                    // 隔离与替换共用同一把行锁：谁先取到谁先提交，后取到的一方
                    // 在锁释放后重新判定，因此"隔离已生效但原图仍被替换销毁"的窗口不存在。
                    ThrowUtils.throwIf(!resourceLockManager.lockResourceRow(
                                    ResourceTypeEnum.PICTURE.getValue(), finalPictureId),
                            ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                    ThrowUtils.throwIf(resourceIsolationService.isQuarantinedForUpdate(
                                    ResourceTypeEnum.PICTURE.getValue(), finalPictureId),
                            ErrorCode.OPERATION_ERROR, "资源处于隔离状态，不能替换");
                }
                // 插入数据
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
                if (finalSpaceId != null) {
                    // 更新空间的使用额度
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }
                // 资源记录与图片记录在同一事务内绑定，避免出现无主对象
                pictureFileService.bindToPicture(picture.getId(), loginUser.getId(), uploadedFiles);
                // 被替换的旧资源在同一事务内标记并清理：数据提交与对象清理属于同一次替换，
                // 不会出现"数据已经换掉、对象清理落在隔离生效之后"的空档
                List<PictureFile> superseded = pictureFileService.supersedeExcept(picture.getId(),
                        uploadedFiles.stream().map(PictureFile::getId).toList());
                if (!superseded.isEmpty()) {
                    this.cleanupReplacedFiles(superseded);
                }
                return picture;
            });
        } catch (RuntimeException e) {
            // 事务回滚后图片记录不存在，登记的资源仍是临时状态：回收对象并删除记录
            discardRegisteredFiles(loginUser.getId(), uploadedFiles, uploadPictureResult);
            throw e;
        }
        picturePreviewService.generateAndUpdatePreview(picture.getId(), uploadPictureResult.getUrl());
        // 可自行实现，如果是更新，可以清理图片资源
        // this.clearPictureFile(oldPicture);
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 上传响应同样只返回受控内容入口
        fillControlledContentUrls(pictureVO, picture);
        return pictureVO;
    }

    /**
     * 上传后失败时回收已写入存储的对象（还没有资源记录可依据）。
     */
    private void discardUploadedObjects(Long userId, UploadPictureResult uploadResult) {
        if (uploadResult != null) {
            String platform = uploadResult.getPlatform();
            // 原图删除失败时不能把缩略图删掉：重试仍需原图定位信息
            if (pictureStorageService.deleteObject(platform, uploadResult.getStoragePath())) {
                pictureStorageService.deleteObject(platform, uploadResult.getThumbnailObjectKey());
            } else {
                log.warn("上传失败后的原图回收未确认: platform={}, key={}",
                        platform, uploadResult.getStoragePath());
            }
        }
        uploadQuotaManager.refundRate(userId, PictureFilePurposeEnum.GALLERY);
    }

    /**
     * 绑定失败时回收已登记的资源：删除对象与临时记录，并回滚当日上传次数。
     */
    private void discardRegisteredFiles(Long userId, List<PictureFile> files,
                                        UploadPictureResult uploadResult) {
        if (files == null || files.isEmpty()) {
            discardUploadedObjects(userId, uploadResult);
            return;
        }
        for (PictureFile file : files) {
            if (!pictureFileService.deleteFile(file)) {
                log.warn("上传失败后的资源回收未完成: fileId={}, kind={}", file.getId(), file.getKind());
            }
        }
        uploadQuotaManager.refundRate(userId, PictureFilePurposeEnum.GALLERY);
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 内容地址一律换成本服务的受控入口，不返回源站长期地址
        fillControlledContentUrls(pictureVO, picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        // 关联查询统计信息
        PictureStats stats = pictureStatsService.getByPictureId(picture.getId());
        if (stats != null) {
            pictureVO.setViewCount(stats.getViewCount());
            pictureVO.setLikeCount(stats.getLikeCount());
            pictureVO.setCollectCount(stats.getCollectCount());
            pictureVO.setDownloadCount(stats.getDownloadCount());
            pictureVO.setShareCount(stats.getShareCount());
            pictureVO.setImpressionCount(stats.getImpressionCount());
            pictureVO.setClickCount(stats.getClickCount());
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 列表与详情使用同一套内容入口；隔离中的图片不返回内容地址
        Set<Long> pictureIdSet = pictureList.stream().map(Picture::getId).collect(Collectors.toSet());
        Set<Long> quarantined = resourceIsolationService.quarantinedIds(
                ResourceTypeEnum.PICTURE.getValue(), pictureIdSet);
        pictureContentService.fillContentUrls(pictureVOList, pictureList);
        if (!quarantined.isEmpty()) {
            for (int i = 0; i < pictureVOList.size(); i++) {
                if (quarantined.contains(pictureList.get(i).getId())) {
                    clearContentUrls(pictureVOList.get(i));
                }
            }
        }
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 关联查询统计信息（一律按 pictureId 关联；统计行自己的主键与图片 ID 无关）
        Map<Long, PictureStats> pictureIdStatsMap = pictureStatsService.mapByPictureIds(pictureIdSet);
        // 3. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
            // 填充统计信息
            PictureStats stats = pictureIdStatsMap.get(pictureVO.getId());
            if (stats != null) {
                pictureVO.setViewCount(stats.getViewCount());
                pictureVO.setLikeCount(stats.getLikeCount());
                pictureVO.setCollectCount(stats.getCollectCount());
                pictureVO.setDownloadCount(stats.getDownloadCount());
                pictureVO.setShareCount(stats.getShareCount());
                pictureVO.setImpressionCount(stats.getImpressionCount());
                pictureVO.setClickCount(stats.getClickCount());
            }
        });
        pictureVOPage.setRecords(pictureVOList);
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
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // codefather.cn?yuwen=dog，应该只保留 codefather.cn
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 有资源记录的图片：按记录里的平台与对象键精确删除，不解析 URL
        List<PictureFile> recorded = pictureFileService.listActiveByPicture(oldPicture.getId());
        if (!recorded.isEmpty()) {
            this.cleanupRecordedFiles(oldPicture.getId(), recorded);
            return;
        }
        // 兼容路径：没有归属记录的存量图片。仅当同一地址仍被其他记录引用时才跳过，
        // 不会因为前缀或域名相同而误删其他对象。
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除原图
        pictureStorageService.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            pictureStorageService.deleteObject(thumbnailUrl);
        }
        // 删除预览图
        String previewUrl = oldPicture.getPreviewUrl();
        if (StrUtil.isNotBlank(previewUrl)) {
            pictureStorageService.deleteObject(previewUrl);
        }
    }

    /**
     * 删除图片关联的资源对象。任一对象删除失败只记录日志并保留记录，
     * 由后续重试或维护清理处理；数据库删除与额度释放已经生效。
     */
    private void cleanupRecordedFiles(Long pictureId, List<PictureFile> files) {
        cleanupFiles(files, "图片资源删除未完成: pictureId=" + pictureId);
    }

    /**
     * 清理被重新上传替换掉的旧资源。
     */
    private void cleanupReplacedFiles(List<PictureFile> superseded) {
        cleanupFiles(superseded, "被替换的资源删除未完成");
    }

    private void cleanupFiles(List<PictureFile> files, String reason) {
        for (PictureFile file : files) {
            if (!pictureFileService.deleteFile(file)) {
                log.warn("{}, fileId={}, kind={}", reason, file.getId(), file.getKind());
            }
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 业务边界最终复核：注解鉴权依赖请求上下文，直接调用服务时不能跳过归属判断
        checkPictureAuth(loginUser, oldPicture);
        // 预检：请求开始前就已隔离的资源直接拒绝
        ThrowUtils.throwIf(isQuarantined(pictureId), ErrorCode.OPERATION_ERROR, "资源处于隔离状态，暂不能删除");
        // 开启事务
        transactionTemplate.execute(status -> {
            // 资源行锁：与隔离、替换共用同一把锁。
            // 拿到锁之后复核隔离状态，隔离若在预检之后生效，这里直接拒绝：
            // 谁先取到锁谁先提交，破坏性动作不会落在隔离生效之后。
            ThrowUtils.throwIf(!resourceLockManager.lockResourceRow(
                            ResourceTypeEnum.PICTURE.getValue(), pictureId),
                    ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            ThrowUtils.throwIf(resourceIsolationService.isQuarantinedForUpdate(
                            ResourceTypeEnum.PICTURE.getValue(), pictureId),
                    ErrorCode.OPERATION_ERROR, "资源处于隔离状态，暂不能删除");
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度（仅当图片属于某个空间时）
            if (oldPicture.getSpaceId() != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 对象清理放在事务之后：图片记录此时已经删除，隔离无法再针对它生效，
        // 因此不会与处置流程抢同一份内容
        this.clearPictureFile(oldPicture);
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
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 空间成员权限：所有者与平台管理员放行，其余按当前数据库成员关系的角色判定。
     *
     * @param loginUser  当前登录用户
     * @param space      目标空间
     * @param permission 需要的权限码
     */
    private void checkSpaceWriteAuth(User loginUser, Space space, String permission) {
        if (userService.isAdmin(loginUser)) {
            return;
        }
        if (space.getUserId() != null && space.getUserId().equals(loginUser.getId())) {
            return;
        }
        if (space.getSpaceType() == null || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, space.getId())
                .eq(SpaceUser::getUserId, loginUser.getId())
                .one();
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(!spaceUserAuthManager.hasPermission(spaceUser.getSpaceRole(), permission),
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
    private boolean isQuarantined(Long pictureId) {
        return pictureId != null
                && resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), pictureId);
    }

    /**
     * 填充受控内容入口，并清掉隔离中图片的入口。
     *
     * <p>隔离不删除内容，但在任何视图中都不再给出可读取的地址；
     * 恢复后按原有归属规则重新可用，不扩大任何人的访问范围。
     */
    private void fillControlledContentUrls(PictureVO pictureVO, Picture picture) {
        pictureContentService.fillContentUrls(pictureVO, picture);
        if (picture != null && picture.getId() != null
                && resourceIsolationService.isQuarantined(
                        ResourceTypeEnum.PICTURE.getValue(), picture.getId())) {
            clearContentUrls(pictureVO);
        }
    }

    private void clearContentUrls(PictureVO pictureVO) {
        if (pictureVO == null) {
            return;
        }
        pictureVO.setUrl(null);
        pictureVO.setThumbnailUrl(null);
        pictureVO.setPreviewUrl(null);
        // 状态本身是元数据：告知已隔离，而不是让客户端以为内容丢失
        pictureVO.setQuarantined(true);
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        // 平台管理员保留违规内容处置能力（独立的管理入口由后续工单负责）
        if (userService.isAdmin(loginUser)) {
            return;
        }
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅作者可修改
            ThrowUtils.throwIf(!picture.getUserId().equals(loginUserId), ErrorCode.NO_AUTH_ERROR);
            return;
        }
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (space.getUserId() != null && space.getUserId().equals(loginUserId)) {
            return;
        }
        if (space.getSpaceType() == null || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅空间所有者可修改
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 团队空间，角色来自数据库成员关系：viewer 只读，editor 及以上可修改
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, loginUserId)
                .one();
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(!spaceUserAuthManager.hasPermission(
                        spaceUser.getSpaceRole(), SpaceUserPermissionConstant.PICTURE_EDIT),
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
        List<PictureVO> pictureVOList = sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        pictureContentService.fillContentUrls(pictureVOList, sortedPictureList);
        return pictureVOList;
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
        List<PictureVO> pictureVOList = pictures.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        pictureContentService.fillContentUrls(pictureVOList, pictures);
        return pictureVOList;
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
        checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
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
}











