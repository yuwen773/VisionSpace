package com.yuwen.visionspace.manager.auth;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private static final long SPACE_ID = 900L;

    @Mock
    private UserService userService;

    @Mock
    private SpaceService spaceService;

    @Mock
    private SpaceUserService spaceUserService;

    @Mock
    private SpaceUserAuthManager spaceUserAuthManager;

    @InjectMocks
    private PictureContentAuthManager manager;

    @BeforeEach
    void setUp() {
        when(userService.isAdmin(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user != null && user.getId() != null && user.getId() == ADMIN;
        });
        when(spaceUserAuthManager.hasPermission(anyString(), anyString())).thenReturn(true);
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
    void privateSpaceContentIsNotReadableAnonymouslyOrByStrangers() {
        Picture picture = spacePicture(SPACE_ID);
        Space space = space(OWNER, SpaceTypeEnum.PRIVATE.getValue());
        when(spaceService.getById(SPACE_ID)).thenReturn(space);

        assertFalse(manager.canReadContent(picture, null));
        assertFalse(manager.canReadContent(picture, user(STRANGER)));
        assertTrue(manager.canReadContent(picture, user(OWNER)));
    }

    @Test
    void teamMemberReadsWhilePresentAndIsDeniedAfterRemoval() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.TEAM.getValue()));
        LambdaQueryChainWrapper<SpaceUser> memberQuery = queryReturning(spaceUser(VIEWER, "viewer"));
        when(spaceUserService.lambdaQuery()).thenReturn(memberQuery);

        assertTrue(manager.canReadContent(picture, user(VIEWER)));

        // 成员被移除后，同一请求上下文的下一次判定必须拒绝
        LambdaQueryChainWrapper<SpaceUser> removedQuery = queryReturning(null);
        when(spaceUserService.lambdaQuery()).thenReturn(removedQuery);
        assertFalse(manager.canReadContent(picture, user(VIEWER)));
    }

    @Test
    void downgradedRoleLosesViewPermission() {
        Picture picture = spacePicture(SPACE_ID);
        when(spaceService.getById(SPACE_ID)).thenReturn(space(OWNER, SpaceTypeEnum.TEAM.getValue()));
        LambdaQueryChainWrapper<SpaceUser> memberQuery = queryReturning(spaceUser(VIEWER, "editor"));
        when(spaceUserService.lambdaQuery()).thenReturn(memberQuery);
        when(spaceUserAuthManager.hasPermission(eq("editor"), anyString())).thenReturn(false);

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

    private SpaceUser spaceUser(long userId, String role) {
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(SPACE_ID);
        spaceUser.setUserId(userId);
        spaceUser.setSpaceRole(role);
        return spaceUser;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private LambdaQueryChainWrapper<SpaceUser> queryReturning(SpaceUser result) {
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<SpaceUser> wrapper = mock(LambdaQueryChainWrapper.class);
        when(wrapper.eq(any(), any())).thenReturn(wrapper);
        when(wrapper.one()).thenReturn(result);
        return wrapper;
    }
}
