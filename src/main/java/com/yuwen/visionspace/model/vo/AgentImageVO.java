package com.yuwen.visionspace.model.vo;

import com.yuwen.visionspace.model.entity.PictureFile;

import java.io.Serializable;

/**
 * Agent 临时图片视图。
 *
 * <p>客户端使用 {@code resourceId} 完成后续绑定与删除。{@code url} 是上传时返回的
 * 存储地址：Agent 会把附件交给模型与工具读取，服务端调用不携带用户 Cookie，
 * 因此这里不能换成需要登录态的受控入口。归属与删除已按资源记录收紧。
 */
public class AgentImageVO implements Serializable {

    private Long resourceId;

    private String url;

    private Long size;

    private static final long serialVersionUID = 1L;

    public static AgentImageVO from(PictureFile record) {
        if (record == null) {
            return null;
        }
        AgentImageVO vo = new AgentImageVO();
        vo.resourceId = record.getId();
        vo.url = record.getUrl();
        vo.size = record.getFileSize();
        return vo;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
