package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

/**
 * 图片行为上报命令。
 *
 * <p>由控制器把 HTTP 请求解析成这个命令：身份字段只以服务端解析结果为准，
 * 客户端声明的用户 id 原样带过来只为「明确拒绝」，不会被当作身份使用。
 */
@Data
public class PictureActionReportCommand {

    /** 目标图片 id */
    private Long pictureId;

    /** 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share */
    private Integer actionType;

    /** 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER */
    private Integer source;

    /** 客户端提供的事件幂等键，可为空 */
    private String eventId;

    /** 客户端声明的用户 id；只要出现就必须拒绝，不作为身份使用 */
    private Long declaredUserId;

    /** 服务端解析出的登录用户 id，null 表示匿名 */
    private Long actorUserId;

    /** 限流与去重使用的客户端标识，必须是脱敏后的值 */
    private String clientKey;
}
