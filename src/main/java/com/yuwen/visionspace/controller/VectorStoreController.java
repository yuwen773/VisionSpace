package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量库管理接口
 */
@Slf4j
@RestController
@RequestMapping("/vector-store")
public class VectorStoreController {

    @Resource
    private PictureEmbeddingService pictureEmbeddingService;

    /**
     * 全量同步站内图片到向量库
     */
    @PostMapping("/full-sync")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> fullSync() {
        log.info("开始全量同步站内图片到向量库");
        int count = pictureEmbeddingService.fullSync();
        return ResultUtils.success(count);
    }
}
