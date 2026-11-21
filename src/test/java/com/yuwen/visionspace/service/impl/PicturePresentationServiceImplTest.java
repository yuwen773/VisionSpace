package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 呈现组装：内容入口、隔离遮蔽、所有者与统计在一条路径上完成。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PicturePresentationServiceImplTest {

    @Mock
    private PictureContentService pictureContentService;

    @Mock
    private ResourceIsolationService resourceIsolationService;

    @Mock
    private UserService userService;

    @Mock
    private PictureStatsService pictureStatsService;

    @InjectMocks
    private PicturePresentationServiceImpl presentationService;

    @Test
    void singlePictureGetsContentOwnerAndStats() {
        Picture picture = picture(1L, 9L);
        stubContentUrls();
        when(resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), 1L))
                .thenReturn(false);
        when(userService.getById(9L)).thenReturn(user(9L));
        when(userService.getUserVO(any(User.class))).thenAnswer(call -> call.getArgument(0) == null
                ? null : new com.yuwen.visionspace.model.vo.UserVO());
        when(pictureStatsService.getByPictureId(1L)).thenReturn(stats(1L, 5L));

        PictureVO vo = presentationService.present(picture);

        assertEquals("/picture/content/1", vo.getUrl());
        assertEquals(5L, vo.getLikeCount());
        assertTrue(vo.getUser() != null, "所有者信息应由组装补齐");
    }

    @Test
    void quarantinedPictureLosesEveryContentEntry() {
        Picture picture = picture(2L, 9L);
        stubContentUrls();
        when(resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), 2L))
                .thenReturn(true);

        PictureVO vo = presentationService.present(picture);

        assertNull(vo.getUrl(), "隔离中的图片不得给出内容地址");
        assertNull(vo.getThumbnailUrl());
        assertNull(vo.getPreviewUrl());
    }

    @Test
    void batchMasksOnlyTheQuarantinedRowAndKeepsIndexAlignment() {
        List<Picture> pictures = List.of(picture(1L, 9L), picture(2L, 9L));
        stubContentUrls();
        when(resourceIsolationService.quarantinedIds(
                ResourceTypeEnum.PICTURE.getValue(), Set.of(1L, 2L))).thenReturn(Set.of(2L));
        when(userService.listByIds(anyCollection())).thenReturn(List.of(user(9L)));
        when(userService.getUserVO(any())).thenReturn(new com.yuwen.visionspace.model.vo.UserVO());
        when(pictureStatsService.mapByPictureIds(anyCollection()))
                .thenReturn(Map.of(1L, stats(1L, 3L)));

        List<PictureVO> result = presentationService.present(pictures);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("/picture/content/1", result.get(0).getUrl());
        assertEquals(3L, result.get(0).getLikeCount());
        assertEquals(2L, result.get(1).getId());
        assertNull(result.get(1).getUrl(), "只有被隔离的那一行失去内容地址");
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertTrue(presentationService.present(List.of()).isEmpty());
        assertNull(presentationService.present((Picture) null));
    }

    /** 内容服务只填受控入口，不关心隔离。 */
    private void stubContentUrls() {
        doAnswer(call -> {
            PictureVO vo = call.getArgument(0);
            Picture picture = call.getArgument(1);
            fillUrls(vo, picture);
            return null;
        }).when(pictureContentService).fillContentUrls(any(PictureVO.class), any(Picture.class));
        doAnswer(call -> {
            List<PictureVO> vos = call.getArgument(0);
            List<Picture> pictures = call.getArgument(1);
            for (int i = 0; i < vos.size(); i++) {
                fillUrls(vos.get(i), pictures.get(i));
            }
            return null;
        }).when(pictureContentService).fillContentUrls(anyList(), anyList());
    }

    private void fillUrls(PictureVO vo, Picture picture) {
        vo.setUrl("/picture/content/" + picture.getId());
        vo.setThumbnailUrl("/picture/content/" + picture.getId() + "/thumb");
        vo.setPreviewUrl("/picture/content/" + picture.getId() + "/preview");
    }

    private Picture picture(Long id, Long userId) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setUserId(userId);
        picture.setName("图片" + id);
        picture.setTags("[]");
        return picture;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserName("user" + id);
        return user;
    }

    private PictureStats stats(Long pictureId, long likes) {
        PictureStats stats = new PictureStats();
        stats.setId(100L + pictureId);
        stats.setLikeCount(likes);
        return stats;
    }
}
