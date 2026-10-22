package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * /user/picture/stats 与 /user/picture/recent 的回归测试。
 * <p>
 * 这两个接口曾在 97230bb 中被删除，但前端 user-center 仍在使用，导致图片 tab 一直静默空数据。
 */
@ExtendWith(MockitoExtension.class)
class UserPictureEndpointsTest {

    @Mock
    private UserService userService;

    @Mock
    private PictureService pictureService;

    @InjectMocks
    private UserController userController;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void statsShouldReturnServiceResultForLoginUser() {
        User loginUser = user(7L);
        when(userService.getLoginUser(any())).thenReturn(loginUser);

        var response = userController.getUserPictureStats(new MockHttpServletRequest());

        assertEquals(0, response.getCode());
        verify(userService).getUserPictureStats(loginUser);
    }

    @Test
    void statsShouldPropagateNotLoggedIn() {
        // getLoginUser 在未登录时抛异常（不会返回 null），异常应原样冒泡
        when(userService.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        assertThrows(BusinessException.class,
                () -> userController.getUserPictureStats(new MockHttpServletRequest()));
        verify(userService, never()).getUserPictureStats(any());
    }

    @Test
    void recentShouldUseLoginUserId() {
        User loginUser = user(7L);
        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(pictureService.getUserRecentPictures(7L, 6)).thenReturn(List.of(new PictureVO()));

        var response = userController.getUserRecentPictures(6, new MockHttpServletRequest());

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
    }

    @Test
    void recentShouldClampCount() {
        User loginUser = user(7L);
        when(userService.getLoginUser(any())).thenReturn(loginUser);

        // 越界值收敛到 [1, 50]，避免超量查询
        userController.getUserRecentPictures(9999, new MockHttpServletRequest());
        userController.getUserRecentPictures(0, new MockHttpServletRequest());
        userController.getUserRecentPictures(-5, new MockHttpServletRequest());

        verify(pictureService).getUserRecentPictures(7L, 50);
        verify(pictureService, times(2)).getUserRecentPictures(7L, 1);
    }

    @Test
    void recentShouldPropagateNotLoggedIn() {
        when(userService.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        assertThrows(BusinessException.class,
                () -> userController.getUserRecentPictures(6, new MockHttpServletRequest()));
        verify(pictureService, never()).getUserRecentPictures(anyLong(), anyInt());
    }
}
