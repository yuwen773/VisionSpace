package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("mcp_server")
public class McpServer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("mcp_server_code")
    private String mcpServerCode;

    @TableField("user_id")
    private Long userId;

    private String name;

    private String description;

    @TableField("deploy_config")
    private String deployConfig;

    private Integer status;

    @TableField("install_type")
    private String installType;

    private String host;

    // 时间列不参与 MyBatis Plus 的自动填充：项目没有注册 MetaObjectHandler，
    // fill 注解只会把未赋值的字段以 NULL 写进 INSERT/UPDATE，反而绕过数据库默认值
    // 与 mcp_server 的 gmt_modified 触发器（两列都是 NOT NULL）。
    @TableField("gmt_create")
    private Date gmtCreate;

    @TableField("gmt_modified")
    private Date gmtModified;
}