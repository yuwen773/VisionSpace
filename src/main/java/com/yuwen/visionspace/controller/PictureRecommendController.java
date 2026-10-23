package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.RecommendPageVO;
import com.yuwen.visionspace.service.PictureRecommendService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/picture/recommend")
public class PictureRecommendController {

    @Resource
    private PictureRecommendService pictureRecommendService;

    @Resource
    private UserService userService;

    /**
     * 获取推荐图片列表
     * @param type 推荐类型: hot(热门), latest(最新), quality(优质), random(随机)
     * @param page 页码 (默认1)
     * @param size 每页数量 (默认10, 最大100)
     * @param context 分页上下文：同一个上下文里的分页结果不重复、不遗漏；
     *                不传时由服务端给出，客户端翻页时带上返回值即可
     */
    @GetMapping("/list")
    public BaseResponse<RecommendPageVO> getRecommendList(
            @RequestParam(name = "type", defaultValue = "hot") String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "context", required = false) String context) {

        size = Math.min(size, 100);
        RecommendPageVO pageVO = pictureRecommendService.getRecommendPictures(type, page, size, context);
        return ResultUtils.success(pageVO);
    }

    /**
     * 手动刷新推荐缓存 (仅管理员)
     */
    @PostMapping("/refresh")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> refreshCache() {
        pictureRecommendService.refreshRecommendCache();
        return ResultUtils.success(true);
    }
}