package com.yuwen.visionspace.manager.auth;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.ResourceAccessGrantService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 内容鉴权矩阵：公开、个人、团队与撤权后的读取判定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureContentAuthManagerTest {

    private static final long OWNER = 1L;
    private static final long VIEWER = 2L;
    private static final long STRANGER = 3L;
    private static final long ADMIN = 4L;
    private static final long MODERATOR = 5L;
    private static final long GRANTEE = 6L;
    private static final long SPACE_ID = 900L;

    @Mock
    private UserService userService;

    @Mock
    private SpaceService spaceService;

    @Mock
    private SpaceAuthority spaceAuthority;

    @Mock
    private ResourceIsolationService resourceIsolationService;

    @Mock
    private ResourceAccessGrantService resourceAccessGrantService;

    @InjectMocks
    private PictureContentAuthManager manager;

    @BeforeEach
    void setUp() {
        when(userService.isAdmin(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user != null && user.getId() != null && user.getId() == ADMIN;
        });
        when(userService.isModerator(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user != null && user.getId() != null && user.getId() == MODERATOR;
        });
        when(resourceIsolationService.isQuarantined(anyString(), any())).thenReturn(false);
        when(resourceAccessGrantService.hasActiveGrant(any(), any())).thenReturn(false);
    }

    @Test
    void quarantineDeniesEveryoneIncludingModerators() {
        Picture approved = publicPicture(1);
        when(resourceIsolationService.isQuarantined(anyString(), any())).thenReturn(true);

        // 隔离优先于公开状态、所有权与显式授权
        assertFalse(manager.canReadContent(approved, null));
        assertFalse(manager.canReadContent(approved, user(OWNER)));
        assertFalse(manager.canReadContent(approved, user(ADMIN)));
        assertFalse(manager.canReadContent(approved, user(GRANTEE)));
        // 处置职责只看元数据：隔离不能成为读取私人内容的后门
        assertFalse(manager.canReadContent(approved, user(MODERATOR)));
    }

    @Test
    void activeGrantAllowsGranteeToReadPrivateContent() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.PRIVATE.getValue()));
        when(resourceAccessGrantService.hasActiveGrant(11L, GRANTEE)).thenReturn(true);

        assertTrue(manager.canReadContent(picture, user(GRANTEE)));
        // 撤销或到期后，同一次判定立即变回拒绝
        when(resourceAccessGrantService.hasActiveGrant(11L, GRANTEE)).thenReturn(false);
        assertFalse(manager.canReadContent(picture, user(GRANTEE)));
        // 授权只对指定资源生效
        assertFalse(manager.canReadContent(picture, user(STRANGER)));
    }

    @Test
    void approvedPublicPictureIsReadableAnonymously() {
        assertTrue(manager.canReadContent(publicPicture(1), null));
    }

    @Test
    void unreviewedPublicPictureIsOnlyReadableByAuthorOrAdmin() {
        Picture pending = publicPicture(0);
        assertFalse(manager.canReadContent(pending, null));
        assertFalse(manager.canReadContent(pending, user(STRANGER)));
        assertTrue(manager.canReadContent(pending, user(OWNER)));
        assertTrue(manager.canReadContent(pending, user(ADMIN)));
    }

    @Test
    void privateSpaceContentIsReadableOnlyByOwner() {
        Picture picture = spacePicture(SPACE_ID);
        Space space = space(OWNER, SpaceTypeEnum.PRIVATE.getValue());
        when(spaceService.getById(SPACE_ID)).thenReturn(space);
        when(spaceAuthority.canOperateSpace(any(Space.class), any(), anyString()))
                .thenAnswer(invocation -> {
                    User viewer = invocation.getArgument(1);
                    return viewer != null && viewer.getId() != null && viewer.getId() == OWNER;
                });

        assertFalse(manager.canReadContent(picture, null));
        assertFalse(manager.canReadContent(picture, user(STRANGER)));
        assertTrue(manager.canReadContent(picture, user(OWNER)));
        // 平台管理员身份不扩展到私人内容；读取需资源方授权或独立处置流程
        assertFalse(manager.canReadContent(picture, user(ADMIN)));
    }

    @Test
    void platformAdminDoesNotGainTeamMembership() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.TEAM.getValue()));

        // 空间判定统一由 SpaceAuthority 给出：平台管理员不因平台角色自动成为团队成员
        assertFalse(manager.canReadContent(picture, user(ADMIN)));
    }

    @Test
    void teamMemberReadsWhilePresentAndIsDeniedAfterRemoval() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.TEAM.getValue()));
        // 每次读取都重新询问空间判定：成员在位时允许，被移除后同一次判定立即拒绝
        when(spaceAuthority.canOperateSpace(any(Space.class), any(), anyString()))
                .thenReturn(true, false);

        assertTrue(manager.canReadContent(picture, user(VIEWER)));
        assertFalse(manager.canReadContent(picture, user(VIEWER)));
    }

    @Test
    void downgradedRoleLosesViewPermission() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.TEAM.getValue()));

        assertFalse(manager.canReadContent(picture, user(VIEWER)));
    }

    @Test
    void missingSpaceDeniesContent() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(null);

        assertFalse(manager.canReadContent(picture, user(OWNER)));
    }

    @Test
    void missingPictureDeniesContent() {
        assertFalse(manager.canReadContent(null, user(OWNER)));
    }

    // ---- fixtures ----

    private Picture publicPicture(int reviewStatus) {
        Picture picture = new Picture();
        picture.setId(10L);
        picture.setUserId(OWNER);
        picture.setReviewStatus(reviewStatus);
        return picture;
    }

    private Picture spacePicture(long spaceId) {
        Picture picture = new Picture();
        picture.setId(11L);
        picture.setUserId(OWNER);
        picture.setSpaceId(spaceId);
        picture.setReviewStatus(1);
        return picture;
    }

    private Space space(long ownerId, int type) {
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(ownerId);
        space.setSpaceType(type);
        return space;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
