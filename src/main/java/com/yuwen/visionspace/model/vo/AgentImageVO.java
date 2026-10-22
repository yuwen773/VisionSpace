package com.yuwen.visionspace.model.vo;

import com.yuwen.visionspace.model.entity.PictureFile;

import java.io.Serializable;

/**
 * Agent 临时图片视图。
 *
 * <p>客户端使用 {@code resourceId} 完成后续绑定与删除；{@code url} 是受控内容入口，
 * 不是源站长期地址。
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
