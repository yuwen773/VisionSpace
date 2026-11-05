package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.model.entity.StorageConfig;
import org.dromara.x.file.storage.core.platform.FileStorage;

/**
 * 存储候选配置验证器。
 *
 * <p>切换或刷新生效快照前，每个启用配置都必须完整构建并验证；
 * 任一项失败即整体失败，调用方保留当前生效快照（D20：候选验证后原子切换）。
 * 验收环境以替身实现替换本接口，记录验证调用并可注入失败。
 */
public interface StorageCandidateVerifier {

    /**
     * 构建并验证单个平台客户端。
     *
     * @return 可注册进运行时快照的 FileStorage 实例
     * @throws com.yuwen.visionspace.exception.BusinessException 平台不支持、必填字段缺失、
     *         客户端初始化失败或探活失败时抛出
     */
    FileStorage buildVerified(StorageConfig config);
}
