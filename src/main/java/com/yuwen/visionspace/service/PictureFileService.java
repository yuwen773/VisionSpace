package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;

import java.util.Collection;
import java.util.List;

/**
 * 图片资源记录服务。
 *
 * <p>上传成功后登记每个对象（原图、缩略图、预览）的所有者、存储平台与对象键；
 * 绑定、替换、删除和内容读取都以这些记录为准，不信任客户端传来的 URL。
 */
public interface PictureFileService extends IService<PictureFile> {

    /** 原图 */
    String KIND_ORIGINAL = "original";

    /** 缩略图 */
    String KIND_THUMBNAIL = "thumbnail";

    /** 预览图 */
    String KIND_PREVIEW = "preview";

    /**
     * 登记一次上传产生的对象。所有者与用途由调用方从服务端会话得出。
     *
     * @param owner       当前登录用户
     * @param purpose     资源用途
     * @param spaceId     空间 id，公共图库为 null
     * @param uploadResult 上传结果
     * @return 已登记的记录（按 original、thumbnail 顺序）
     */
    List<PictureFile> registerUpload(User owner, PictureFilePurposeEnum purpose, Long spaceId,
                                     UploadPictureResult uploadResult);

    /**
     * 把图片记录绑定到刚登记的资源上（临时资源转为有效）。
     */
    void bindToPicture(Long pictureId, Long ownerUserId, List<PictureFile> files);

    /**
     * 标记图片的旧资源为已替换，并返回需要清理的对象。
     *
     * @param pictureId      图片 id
     * @param keptFileIds    新资源记录 id；同一张图的其他记录将被替换
     * @return 被替换的记录
     */
    List<PictureFile> supersedeExcept(Long pictureId, List<Long> keptFileIds);

    /**
     * 按图片与内容类型查找当前有效资源。
     */
    PictureFile findActive(Long pictureId, String kind);

    /**
     * 按图片列出当前有效资源。
     */
    List<PictureFile> listActiveByPicture(Long pictureId);

    /**
     * 批量列出多张图片的当前有效资源，供列表视图一次性解析内容入口。
     */
    List<PictureFile> listActiveByPictures(Collection<Long> pictureIds);

    /**
     * 按上传返回的地址精确查找有效资源。只接受与记录完全一致的地址，
     * 不做前缀或域名匹配，避免未知文件被自动认领。
     */
    PictureFile findByExactUrl(String url);

    /**
     * 删除单个资源记录对应的对象。对象删除失败时记录保留，调用方可重试。
     *
     * @return 是否确认删除（含"记录已不存在"）
     */
    boolean deleteFile(PictureFile file);

    /**
     * 用户当前保留（未删除）的资源总字节数，用于用户级额度判定。
     */
    long usedBytes(Long ownerUserId);
}
