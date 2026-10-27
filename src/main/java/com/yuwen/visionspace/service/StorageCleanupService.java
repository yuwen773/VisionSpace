package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.StorageCleanupTask;

import java.util.List;

/**
 * 对象存储清理任务的登记与重试。
 *
 * <p>数据库与对象存储没有共同的事务：数据库回滚之后对象仍在存储里，数据库提交
 * 之后旧对象也可能还没删掉。这里把"还需要删除的对象"落成可重试的记录，
 * 让失败可见、可重试、可核对，而不是只写一行日志。
 */
public interface StorageCleanupService extends IService<StorageCleanupTask> {

    /**
     * 登记一条待重试的删除任务。
     *
     * <p>同一个对象键、同一种内容类型只保留一条待处理任务。
     *
     * @param file   资源记录；上传失败、还没有记录时传 null
     * @param key    对象键，来自服务端（不是客户端地址）
     * @param reason 触发原因
     */
    void enqueue(PictureFile file, String key, String reason);

    /**
     * 登记一条等待人工处理的清理任务。
     *
     * <p>用于只有地址、没有平台与对象键的存量对象：按地址猜测删除会误删别人的
     * 对象，因此不进入自动重试，直接停在「等待人工处理」供核对。
     */
    void enqueueForReview(String url, String reason);

    /**
     * 到期待重试的任务。
     */
    List<StorageCleanupTask> listDue(int limit);

    /**
     * 重试一条任务。
     *
     * <p>删除前重新核对引用：对象仍被有效资源记录引用时任务转为阻断，不删除对象。
     *
     * @return 是否确认删除
     */
    boolean retryOne(Long taskId);
}
