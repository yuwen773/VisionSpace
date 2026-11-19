package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * 向量库管理接口
 */
@Slf4j
@RestController
@RequestMapping("/vector-store")
@ConditionalOnProperty(prefix = "vision-space.rag", name = "enabled", havingValue = "true")
public class VectorStoreController {

    @Resource
    private PictureEmbeddingService pictureEmbeddingService;

    /**
     * 全量同步站内图片到向量库
     */
    @PostMapping("/full-sync")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> fullSync() {
        log.info("触发全量同步站内图片到向量库");
        CompletableFuture.runAsync(() -> {
            try {
                int count = pictureEmbeddingService.fullSync();
                log.info("全量同步完成, count={}", count);
            } catch (Exception e) {
                log.error("全量同步失败", e);
            }
        });
        return ResultUtils.success("全量同步已触发，请查看日志了解进度");
    }
}
