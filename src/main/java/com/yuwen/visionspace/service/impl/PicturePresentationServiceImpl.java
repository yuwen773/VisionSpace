package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PicturePresentationService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片行呈现的组装实现。
 *
 * <p>顺序固定：先转 VO，再填受控内容入口，再按隔离状态遮蔽，最后补所有者与统计。
 * 顺序反了就会给隔离中的图片留下地址，或在错误的基础上算统计。
 */
@Service
public class PicturePresentationServiceImpl implements PicturePresentationService {

    @Resource
    private PictureContentService pictureContentService;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Resource
    private UserService userService;

    @Resource
    private PictureStatsService pictureStatsService;

    @Override
    public PictureVO present(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = PictureVO.objToVo(picture);
        pictureContentService.fillContentUrls(pictureVO, picture);
        if (isQuarantined(picture.getId())) {
            clearContentUrls(pictureVO);
        }
        User user = picture.getUserId() == null ? null : userService.getById(picture.getUserId());
        pictureVO.setUser(userService.getUserVO(user));
        fillStats(pictureVO, pictureStatsService.getByPictureId(picture.getId()));
        return pictureVO;
    }

    @Override
    public List<PictureVO> present(List<Picture> pictures) {
        if (pictures == null || pictures.isEmpty()) {
            return new ArrayList<>();
        }
        List<Picture> rows = pictures.stream().filter(Objects::nonNull).toList();
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<PictureVO> pictureVOs = rows.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        pictureContentService.fillContentUrls(pictureVOs, rows);

        Set<Long> pictureIds = rows.stream().map(Picture::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> quarantined = pictureIds.isEmpty() ? Set.of()
                : resourceIsolationService.quarantinedIds(
                        ResourceTypeEnum.PICTURE.getValue(), pictureIds);
        if (!quarantined.isEmpty()) {
            for (int i = 0; i < pictureVOs.size(); i++) {
                if (quarantined.contains(rows.get(i).getId())) {
                    clearContentUrls(pictureVOs.get(i));
                }
            }
        }

        Map<Long, List<User>> usersById = loadUsers(rows);
        Map<Long, PictureStats> statsByPicture = pictureIds.isEmpty() ? Map.of()
                : pictureStatsService.mapByPictureIds(pictureIds);
        for (PictureVO pictureVO : pictureVOs) {
            List<User> owners = usersById.get(pictureVO.getUserId());
            pictureVO.setUser(userService.getUserVO(owners == null || owners.isEmpty() ? null : owners.get(0)));
            fillStats(pictureVO, statsByPicture.get(pictureVO.getId()));
        }
        return pictureVOs;
    }

    private Map<Long, List<User>> loadUsers(List<Picture> pictures) {
        Set<Long> userIds = pictures.stream()
                .map(Picture::getUserId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.groupingBy(User::getId));
    }

    private boolean isQuarantined(Long pictureId) {
        return pictureId != null && resourceIsolationService.isQuarantined(
                ResourceTypeEnum.PICTURE.getValue(), pictureId);
    }

    private void clearContentUrls(PictureVO pictureVO) {
        pictureVO.setUrl(null);
        pictureVO.setThumbnailUrl(null);
        pictureVO.setPreviewUrl(null);
        // 状态本身是元数据：告知已隔离，而不是让客户端以为内容丢失
        pictureVO.setQuarantined(true);
    }

    private void fillStats(PictureVO pictureVO, PictureStats stats) {
        if (stats == null) {
            return;
        }
        pictureVO.setViewCount(stats.getViewCount());
        pictureVO.setLikeCount(stats.getLikeCount());
        pictureVO.setCollectCount(stats.getCollectCount());
        pictureVO.setDownloadCount(stats.getDownloadCount());
        pictureVO.setShareCount(stats.getShareCount());
        pictureVO.setImpressionCount(stats.getImpressionCount());
        pictureVO.setClickCount(stats.getClickCount());
    }
}
