package com.yuwen.visionspace;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@EnableAsync
@EnableFileStorage
@MapperScan("com.yuwen.visionspace.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class VisionSpaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisionSpaceApplication.class, args);
    }

}
