CREATE TABLE `user_mcp_preference` (
  `id`                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  `userId`               BIGINT      NOT NULL UNIQUE COMMENT '用户ID',
  `defaultEnabledServers` TEXT        COMMENT '默认启用的MCP服务列表(JSON数组)',
  `gmtCreate`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户MCP偏好配置表';
