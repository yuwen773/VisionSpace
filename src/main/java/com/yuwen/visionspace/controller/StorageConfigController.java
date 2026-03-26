package com.yuwen.visionspace.controller;

import cn.hutool.core.bean.BeanUtil;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.DeleteRequest;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.storage.StorageConfigActiveRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigAddRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigUpdateRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.vo.StorageConfigVO;
import com.yuwen.visionspace.service.StorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/storage/config")
@RequiredArgsConstructor
@Slf4j
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    /**
     * 获取存储配置列表
     */
    @GetMapping("/list")
    public BaseResponse<List<StorageConfigVO>> listStorageConfig(StorageConfigQueryRequest queryRequest,
                                                                 HttpServletRequest request) {
        List<StorageConfigVO> list = storageConfigService.listStorageConfigVO(queryRequest, request);
        return ResultUtils.success(list);
    }

    /**
     * 添加存储配置
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addStorageConfig(@RequestBody StorageConfigAddRequest addRequest) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        StorageConfig storageConfig = new StorageConfig();
        BeanUtil.copyProperties(addRequest, storageConfig);
        long id = storageConfigService.addStorageConfig(storageConfig);
        return ResultUtils.success(id);
    }

    /**
     * 更新存储配置
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateStorageConfig(@RequestBody StorageConfigUpdateRequest updateRequest) {
        ThrowUtils.throwIf(updateRequest == null || updateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        StorageConfig storageConfig = new StorageConfig();
        BeanUtil.copyProperties(updateRequest, storageConfig);
        boolean result = storageConfigService.updateStorageConfig(storageConfig);
        return ResultUtils.success(result);
    }

    /**
     * 删除存储配置
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteStorageConfig(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = storageConfigService.deleteStorageConfig(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 激活指定平台
     */
    @PostMapping("/set_active")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setActivePlatform(@RequestBody StorageConfigActiveRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = storageConfigService.setActivePlatform(request);
        return ResultUtils.success(result);
    }

    /**
     * 设置默认平台
     */
    @PostMapping("/set_default")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setDefaultPlatform(@RequestBody StorageConfigActiveRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = storageConfigService.setDefaultPlatform(request.getId());
        return ResultUtils.success(result);
    }
}
