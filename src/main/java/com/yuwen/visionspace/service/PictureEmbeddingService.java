package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.Picture;

/**
 * 图片嵌入向量服务
 * 负责将图片元数据转化为向量并存储到向量库
 *
 * <p>索引的内容由图片当前在数据库里的状态决定，不由调用方手里那个改动前或
 * 改动后的对象快照决定：调用方只给出图片 id，同步时重新读一次当前状态，
 * 再决定覆盖写入还是从索引移除。
 */
public interface PictureEmbeddingService {

    /**
     * 依据数据库当前状态同步一张图片的索引。异步执行，不阻塞调用方。
     *
     * <p>失败时留下可重试的记录，由重试任务按退避时间重做；重做时同样以数据库
     * 的当前状态为准，因此中途发生的编辑、撤审与删除都会被带上。
     *
     * @param pictureId 图片 id
     */
    void syncPictureIndex(Long pictureId);

    /**
     * 同步执行一次索引同步。调用方等待结果：重试任务与验收用例使用这个入口。
     *
     * @param pictureId 图片 id
     */
    void syncPictureIndexNow(Long pictureId);

    /**
     * 全量同步：为所有符合条件的图片生成嵌入，并把已不该被检索的图片移出索引。
     *
     * @return 处理的图片数量
     */
    int fullSync();
}
