package com.yuwen.visionspace.model.dto.space;

import com.yuwen.visionspace.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询空间请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 空间状态：0-禁用 1-正常
     */
    private Integer status;

    /**
     * 所有者关键字：匹配用户名或账号，用于管理端按人筛空间
     */
    private String userKeyword;

    private static final long serialVersionUID = 1L;
}