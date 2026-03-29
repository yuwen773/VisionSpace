// package com.yuwen.visionspace.manager.sharding;

// import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
// import com.yuwen.visionspace.model.entity.Space;
// import com.yuwen.visionspace.model.enums.SpaceLevelEnum;
// import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
// import com.yuwen.visionspace.service.SpaceService;
// import lombok.extern.slf4j.Slf4j;
// import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
// import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
// import org.apache.shardingsphere.mode.manager.ContextManager;
// import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
// import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
// import org.apache.shardingsphere.sharding.rule.ShardingRule;

// import jakarta.annotation.PostConstruct;
// import jakarta.annotation.Resource;
// import javax.sql.DataSource;
// import java.sql.SQLException;
// import java.util.Collections;
// import java.util.List;
// import java.util.Optional;
// import java.util.Set;
// import java.util.stream.Collectors;

// //@Component
// @Slf4j
// public class DynamicShardingManager {

//     @Resource
//     private DataSource dataSource;

//     @Resource
//     private SpaceService spaceService;

//     private static final String LOGIC_TABLE_NAME = "picture";

//     private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称

//     @PostConstruct
//     public void init() {
//         log.info("DynamicShardingManager 初始化完成");
//     }
