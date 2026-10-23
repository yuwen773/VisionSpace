package com.yuwen.visionspace.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.outbound.OutboundDownloader;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * URL 图片上传。
 *
 * <p>地址校验与实际下载都走 {@link OutboundDownloader}：协议、主机、解析结果、
 * 每一跳重定向、实际字节数与耗时都在同一条策略下判定，不再只靠 HEAD 预检——
 * HEAD 被拒绝或返回异常时，安全检查仍会在实际 GET 上完整执行。
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    /** 允许的图片类型，与文件上传保持一致。 */
    static final List<String> ALLOW_CONTENT_TYPES =
            Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");

    @Resource
    private OutboundDownloader outboundDownloader;

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");
        // 只做地址策略判定，不发起请求；真正的类型/大小检查在下载时按实际响应执行
        outboundDownloader.assertAllowed(fileUrl);
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 只取路径部分，查询串不参与命名；路径末段作为原始文件名
        String path = URI.create(fileUrl.trim()).getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        if (StrUtil.isBlank(name)) {
            return "download.jpg";
        }
        // 没有扩展名时补一个，避免生成的对象键缺少后缀
        if (StrUtil.isBlank(FileUtil.getSuffix(name))) {
            return name + ".jpg";
        }
        return name;
    }

    @Override
    protected void processFile(Object inputSource, File file) {
        String fileUrl = (String) inputSource;
        outboundDownloader.download(fileUrl, file, ALLOW_CONTENT_TYPES);
    }
}
