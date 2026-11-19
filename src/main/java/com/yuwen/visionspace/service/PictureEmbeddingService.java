package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.Picture;

/**
 * 图片嵌入向量服务
 * 负责将图片元数据转化为向量并存储到 ChromaDB
 */
public interface PictureEmbeddingService {

    /**
     * 为单张图片生成嵌入并存储
     */
    void generateAndStoreEmbedding(Picture picture);

    /**
     * 更新图片的嵌入向量（元数据变更时）
     */
    void updateEmbedding(Picture picture);

    /**
     * 从向量库中删除图片嵌入
     */
    void deleteEmbedding(Long pictureId);

    /**
     * 全量同步：为所有符合条件的图片生成嵌入
     *
     * @return 处理的图片数量
     */
    int fullSync();
}
