package com.yuwen.visionspace.controller;

import cn.hutool.core.bean.BeanUtil;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.DeleteRequest;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.StorageSwitchService;
import com.yuwen.visionspace.model.dto.storage.StorageConfigAddRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigQueryRequest;
import com.yuwen.visionspace.model.dto.storage.StorageConfigUpdateRequest;
import com.yuwen.visionspace.model.dto.storage.StorageSwitchRequest;
import com.yuwen.visionspace.model.entity.StorageConfig;
import com.yuwen.visionspace.model.vo.StorageConfigVO;
import com.yuwen.visionspace.model.vo.StorageSwitchStatusVO;
import com.yuwen.visionspace.service.StorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/storage/config")
@RequiredArgsConstructor
@Slf4j
public class StorageConfigController {

    private final StorageConfigService storageConfigService;
    private final StorageSwitchService storageSwitchService;

    /**
     * 获取存储配置列表（仅管理员）
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
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
     * 切换默认存储平台（唯一的平台切换入口）。
     *
     * <p>候选完整验证 + 原子占位 + 本实例生效；响应是切换状态而非布尔值：
     * expectedInstances 大于 1 时返回 pending 状态，需要其余实例确认后才
     * 晋升为完整成功。单实例部署省略 expectedInstances 即立即晋升。
     */
    @PostMapping("/set_active")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StorageSwitchStatusVO> setActivePlatform(@RequestBody StorageSwitchRequest request) {
        ThrowUtils.throwIf(request == null || request.getTargetConfigId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(storageSwitchService.switchToStoragePlatform(request));
    }

    /**
     * 本实例确认候选版本生效；全部期望实例确认后晋升为完整成功
     */
    @PostMapping("/switch/confirm")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StorageSwitchStatusVO> confirmStorageSwitch() {
        return ResultUtils.success(storageSwitchService.confirmStorageSwitch());
    }

    /**
     * 回滚进行中的切换：保留旧快照，activeVersion 不变
     */
    @PostMapping("/switch/rollback")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StorageSwitchStatusVO> rollbackStorageSwitch() {
        return ResultUtils.success(storageSwitchService.rollbackStorageSwitch());
    }

    /**
     * 查询真实生效状态：持久激活配置、全局版本与本实例版本（不含任何凭据）
     */
    @GetMapping("/switch/status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StorageSwitchStatusVO> getStorageSwitchStatus() {
        return ResultUtils.success(storageSwitchService.getStorageSwitchStatus());
    }

    /**
     * 从持久配置重建本实例运行快照（版本对齐 activeVersion）。
     * 构建失败保留旧快照并显式报错。
     */
    @PostMapping("/refresh")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> refreshStorageConfig() {
        storageSwitchService.reloadPersistedSnapshot();
        return ResultUtils.success(true);
    }
}
