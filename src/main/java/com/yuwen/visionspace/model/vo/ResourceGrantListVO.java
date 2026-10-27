package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 某资源的授权列表视图。
 *
 * <p>除授权记录外，还给出当前查看者是否具备管理该资源授权的资格：
 * 授予与撤销的资格由服务端判定，客户端据此显示入口，不再自行猜测角色。
 */
@Data
public class ResourceGrantListVO implements Serializable {

    /** 当前查看者是否可以对该资源授予或撤销授权 */
    private boolean canManage;

    /** 授权记录（含已撤销与已到期） */
    private List<ResourceGrantVO> grants;

    private static final long serialVersionUID = 1L;
}
