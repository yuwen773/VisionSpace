package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.PictureVO;

import java.util.List;

/**
 * 图片实际内容的统一入口。
 *
 * <p>浏览器不再直接使用源站长期地址：原图、缩略图、预览与下载都经由本服务，
 * 每次新请求都按当前角色重新判定。未登记资源记录的存量图片按未知归属处理，
 * 不自动授权。
 */
public interface PictureContentService {

    /**
     * 内容类型。
     */
    enum ContentKind {
        ORIGINAL("original"),
        THUMBNAIL("thumbnail"),
        PREVIEW("preview");

        private final String value;

        ContentKind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * 解析出的内容负载。
     *
     * @param bytes       内容
     * @param contentType MIME 类型
     * @param downloadName 下载时的文件名，可为空
     */
    record Content(byte[] bytes, String contentType, String downloadName) {
    }

    /**
     * 读取图片内容。每次调用都重新判定权限。
     *
     * @param pictureId 图片 id
     * @param kind      内容类型
     * @param viewer    当前登录用户，匿名请求为 null
     * @param download  是否为下载（影响文件名与处置头）
     * @return 内容
     */
    Content loadContent(Long pictureId, ContentKind kind, User viewer, boolean download);

    /**
     * 为图片视图填充受控的内容入口地址。
     *
     * <p>返回的是本服务的内容入口，而不是源站地址；没有资源记录的历史图片返回 null，
     * 由调用方按未知归属处理。
     *
     * @param picture 图片记录
     * @param kind    内容类型
     * @return 内容入口地址，无资源记录时为 null
     */
    String contentUrl(Picture picture, ContentKind kind);

    /**
     * 为图片视图填充受控的内容入口地址（覆盖视图里的 url / thumbnailUrl / previewUrl）。
     *
     * <p>图库、空间与个人中心的视图统一走这里，避免各入口自行决定是否返回源站地址。
     * 没有资源记录的存量图片保留元数据，但内容地址置空——未知归属不自动授权。
     *
     * @param pictureVO 视图
     * @param picture   图片记录
     */
    void fillContentUrls(PictureVO pictureVO, Picture picture);

    /**
     * 批量填充内容入口地址。
     */
    void fillContentUrls(List<PictureVO> pictureVOs, List<Picture> pictures);
}
