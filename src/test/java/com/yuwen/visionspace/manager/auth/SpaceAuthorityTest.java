package com.yuwen.visionspace.manager.auth;

import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.mapper.SpaceUserMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceRoleEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.PICTURE_DELETE;
import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.PICTURE_EDIT;
import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.PICTURE_UPLOAD;
import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;
import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.SPACE_USER_MANAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ADR-0001 判定矩阵：团队身份、平台管理身份与私人归属分别判定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpaceAuthorityTest {

    private static final long OWNER = 1L;
    private static final long MEMBER = 2L;
    private static final long STRANGER = 3L;
    private static final long PLATFORM_ADMIN = 4L;
    private static final long SPACE_ID = 900L;

    private static final List<String> VIEWER_PERMISSIONS = List.of(PICTURE_VIEW);
    private static final List<String> EDITOR_PERMISSIONS =
            List.of(PICTURE_VIEW, PICTURE_UPLOAD, PICTURE_EDIT, PICTURE_DELETE);
    private static final List<String> ADMIN_PERMISSIONS =
            List.of(SPACE_USER_MANAGE, PICTURE_VIEW, PICTURE_UPLOAD, PICTURE_EDIT, PICTURE_DELETE);

    @Mock
    private UserService userService;

    @Mock
    private SpaceMapper spaceMapper;

    @Mock
    private SpaceUserMapper spaceUserMapper;

    @Spy
    private SpaceUserAuthManager spaceUserAuthManager = new SpaceUserAuthManager();

    @InjectMocks
    private SpaceAuthority authority;

    @BeforeEach
    void setUp() {
        when(userService.isAdmin(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user != null && Long.valueOf(PLATFORM_ADMIN).equals(user.getId());
        });
    }

    @Test
    void publicGalleryPersonalPictureBelongsToItsAuthor() {
        Picture picture = publicPicture(OWNER);

        assertEquals(ADMIN_PERMISSIONS, authority.picturePermissions(picture, user(OWNER)));
        // 其他登录用户只能查看，平台管理员也不例外（审核处置走独立入口）
        assertEquals(VIEWER_PERMISSIONS, authority.picturePermissions(picture, user(MEMBER)));
        assertEquals(VIEWER_PERMISSIONS, authority.picturePermissions(picture, user(PLATFORM_ADMIN)));
        assertEquals(List.of(), authority.picturePermissions(picture, null));

        assertTrue(authority.canOperatePicture(picture, user(OWNER), PICTURE_EDIT));
        assertFalse(authority.canOperatePicture(picture, user(MEMBER), PICTURE_EDIT));
        assertFalse(authority.canOperatePicture(picture, user(PLATFORM_ADMIN), PICTURE_DELETE));
    }

    @Test
    void publicGalleryFallbackGrantsUploadToLoggedInUsersOnly() {
        assertEquals(ADMIN_PERMISSIONS, authority.spacePermissions(null, user(PLATFORM_ADMIN)));
        assertEquals(List.of(PICTURE_VIEW, PICTURE_UPLOAD), authority.spacePermissions(null, user(MEMBER)));
        assertEquals(List.of(), authority.spacePermissions(null, null));
    }

    @Test
    void privateSpaceIsOwnedByItsOwnerAndClosedToPlatformAdmins() {
        Space space = space(OWNER, SpaceTypeEnum.PRIVATE.getValue());

        assertEquals(ADMIN_PERMISSIONS, authority.spacePermissions(space, user(OWNER)));
        assertEquals(List.of(), authority.spacePermissions(space, user(STRANGER)));
        // 平台管理员不因平台角色读取私人空间
        assertEquals(List.of(), authority.spacePermissions(space, user(PLATFORM_ADMIN)));

        assertTrue(authority.canOperateSpace(space, user(OWNER), PICTURE_DELETE));
        assertFalse(authority.canOperateSpace(space, user(PLATFORM_ADMIN), PICTURE_VIEW));
        assertFalse(authority.canManageMembers(space, user(PLATFORM_ADMIN)));
        assertTrue(authority.canEditSpaceMetadata(space, user(PLATFORM_ADMIN)));
        assertFalse(authority.canEditSpaceMetadata(space, user(STRANGER)));
    }

    @Test
    void teamViewerOnlyReads() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        when(spaceUserMapper.selectOne(any())).thenReturn(member(MEMBER, SpaceRoleEnum.VIEWER));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);

        assertEquals(VIEWER_PERMISSIONS, authority.spacePermissions(space, user(MEMBER)));
        assertTrue(authority.canOperateSpace(space, user(MEMBER), PICTURE_VIEW));
        assertFalse(authority.canOperateSpace(space, user(MEMBER), PICTURE_UPLOAD));
        assertFalse(authority.canManageMembers(space, user(MEMBER)));
        assertFalse(authority.canGrantResource(pictureInSpace(OWNER), user(MEMBER)));
    }

    @Test
    void teamEditorUploadsAndEditsButCannotManageMembersOrGrant() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        when(spaceUserMapper.selectOne(any())).thenReturn(member(MEMBER, SpaceRoleEnum.EDITOR));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);

        assertEquals(EDITOR_PERMISSIONS, authority.spacePermissions(space, user(MEMBER)));
        assertTrue(authority.canOperateSpace(space, user(MEMBER), PICTURE_EDIT));
        assertTrue(authority.canOperatePicture(pictureInSpace(OWNER), user(MEMBER), PICTURE_DELETE));
        assertFalse(authority.canManageMembers(space, user(MEMBER)));
        assertFalse(authority.canGrantResource(pictureInSpace(OWNER), user(MEMBER)));
    }

    @Test
    void teamAdminManagesMembersAndGrants() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());

        assertTrue(authority.canManageMembers(space, user(OWNER)));
        when(spaceUserMapper.selectOne(any())).thenReturn(member(MEMBER, SpaceRoleEnum.ADMIN));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);

        assertEquals(ADMIN_PERMISSIONS, authority.spacePermissions(space, user(MEMBER)));
        assertTrue(authority.canManageMembers(space, user(MEMBER)));
        assertTrue(authority.canGrantResource(pictureInSpace(OWNER), user(MEMBER)));
    }

    @Test
    void nonMemberAndPlatformAdminGainNothingFromTeamSpace() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        when(spaceUserMapper.selectOne(any())).thenReturn(null);

        assertEquals(List.of(), authority.spacePermissions(space, user(STRANGER)));
        assertEquals(List.of(), authority.spacePermissions(space, user(PLATFORM_ADMIN)));
        assertFalse(authority.canOperateSpace(space, user(PLATFORM_ADMIN), PICTURE_VIEW));
        assertFalse(authority.canManageMembers(space, user(PLATFORM_ADMIN)));
    }

    @Test
    void membershipIsQueriedFreshOnEveryDecision() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        // 在位 → 被移除：同一请求上下文的后续判定必须立即失效
        when(spaceUserMapper.selectOne(any()))
                .thenReturn(member(MEMBER, SpaceRoleEnum.VIEWER), (SpaceUser) null);

        assertTrue(authority.canOperateSpace(space, user(MEMBER), PICTURE_VIEW));
        assertFalse(authority.canOperateSpace(space, user(MEMBER), PICTURE_VIEW));
    }

    @Test
    void spacePicturePermissionsFollowTheSpace() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);
        when(spaceUserMapper.selectOne(any())).thenReturn(member(MEMBER, SpaceRoleEnum.VIEWER));

        assertEquals(VIEWER_PERMISSIONS, authority.picturePermissions(pictureInSpace(OWNER), user(MEMBER)));

        // 平台管理员不是成员：空间判定必须重新查成员关系，而不是复用上一次结果
        when(spaceUserMapper.selectOne(any())).thenReturn(null);
        assertEquals(List.of(), authority.picturePermissions(pictureInSpace(OWNER), user(PLATFORM_ADMIN)));
    }

    @Test
    void missingSpaceDeniesEverySpaceDecision() {
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(null);

        assertFalse(authority.canViewSpacePictures(SPACE_ID, user(OWNER)));
        assertFalse(authority.canViewSpacePictures(null, user(OWNER)));
        assertFalse(authority.canOperatePicture(pictureInSpace(OWNER), user(OWNER), PICTURE_EDIT));
        assertFalse(authority.canGrantResource(pictureInSpace(OWNER), user(OWNER)));
        assertEquals(List.of(), authority.picturePermissions(pictureInSpace(OWNER), user(OWNER)));
        assertFalse(authority.canEditSpaceMetadata(null, user(OWNER)));
    }

    @Test
    void canViewSpacePicturesMatchesSpaceReadAccess() {
        Space space = space(OWNER, SpaceTypeEnum.TEAM.getValue());
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space);
        when(spaceUserMapper.selectOne(any())).thenReturn(member(MEMBER, SpaceRoleEnum.VIEWER));

        assertTrue(authority.canViewSpacePictures(SPACE_ID, user(OWNER)));
        assertTrue(authority.canViewSpacePictures(SPACE_ID, user(MEMBER)));

        when(spaceUserMapper.selectOne(any())).thenReturn(null);
        assertFalse(authority.canViewSpacePictures(SPACE_ID, user(STRANGER)));
    }

    // ---- fixtures ----

    private Picture publicPicture(long authorId) {
        Picture picture = new Picture();
        picture.setId(10L);
        picture.setUserId(authorId);
        return picture;
    }

    private Picture pictureInSpace(long authorId) {
        Picture picture = new Picture();
        picture.setId(11L);
        picture.setUserId(authorId);
        picture.setSpaceId(SPACE_ID);
        return picture;
    }

    private Space space(long ownerId, int type) {
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(ownerId);
        space.setSpaceType(type);
        return space;
    }

    private SpaceUser member(long userId, SpaceRoleEnum role) {
        SpaceUser membership = new SpaceUser();
        membership.setSpaceId(SPACE_ID);
        membership.setUserId(userId);
        membership.setSpaceRole(role.getValue());
        return membership;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
