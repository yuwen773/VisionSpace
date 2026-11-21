package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.dto.picture.PictureUploadByBatchRequest;
import com.yuwen.visionspace.model.dto.picture.PictureUploadRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.PictureVO;

/**
 * 图片入库生命周期：上传、替换、批量抓取，以及失败时的资源补偿。
 *
 * <p>对象存储、配额预留与补偿协议都留在这个模块内部；调用方只关心
 * 上传结果与失败原因，不需要知道临时对象如何回收。
 */
public interface PictureUploadService {

    /**
     * 上传图片（新增或替换已有图片）。
     *
     * @param inputSource          文件输入源或 URL
     * @param pictureUploadRequest 上传参数（空间、图片名、替换目标）
     * @param loginUser            当前登录用户
     * @return 上传后的图片视图
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 按关键词批量抓取并上传图片。
     *
     * @param request   批量抓取参数
     * @param loginUser 当前登录用户
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest request,
                                 User loginUser);
}
