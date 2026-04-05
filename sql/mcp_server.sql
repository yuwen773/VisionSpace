CREATE TABLE `mcp_server` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `mcpServerCode`  VARCHAR(64)  NOT NULL COMMENT '服务器编码',
  `userId`         BIGINT      NOT NULL COMMENT '所属用户ID',
  `name`           VARCHAR(128) NOT NULL COMMENT '服务器名称',
  `description`    VARCHAR(512) COMMENT '描述',
  `deployConfig`   TEXT         NOT NULL COMMENT '部署配置(JSON)',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用 1=启用 3=删除',
  `installType`    VARCHAR(32)  NOT NULL DEFAULT 'SSE' COMMENT '安装类型',
  `host`           VARCHAR(256) COMMENT 'MCP服务器地址',
  `gmtCreate`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_mcp_server_code_user` (`mcpServerCode`, `userId`),
  KEY `idx_user_id` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务器配置表';
