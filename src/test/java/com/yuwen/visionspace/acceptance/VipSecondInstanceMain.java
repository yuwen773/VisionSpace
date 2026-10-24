package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.VisionSpaceApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * #39 双实例验收的第二实例入口：真实第二 JVM 进程，共享同一隔离 MySQL/Redis。
 *
 * <p>隔离由既有机制保证（沿用 {@link IsolatedApplicationInitializer} 的强制校验）：
 * 第二实例只接受验收脚本注入的环境变量，库/Redis 标记不匹配立即失败，
 * 不存在回退到本地配置的路径。该进程经脚本启动，结果通过文件握手。
 */
public final class VipSecondInstanceMain {

    private VipSecondInstanceMain() {
    }

    public static void main(String[] args) throws Exception {
        // args[0] = 就绪端口文件路径；启动成功（容器可服务）后写入实际端口
        Path readyFile = Path.of(args[0]);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(VisionSpaceApplication.class)
                .initializers(new IsolatedApplicationInitializer())
                .properties(defaults())
                .run(args)) {
            // RANDOM_PORT 下实际端口在容器就绪后才能读取
            String port = context.getEnvironment().getProperty("local.server.port");
            if (port == null) {
                throw new IllegalStateException("second instance did not start on a local port");
            }
            Files.writeString(readyFile, port, StandardCharsets.UTF_8);
            System.out.println("VipSecondInstance ready on port " + port);
            // 保持进程存活直到测试结束写 stop 文件，或 JVM 被 kill
            Path stopFile = Path.of(args[0] + ".stop");
            while (!Files.exists(stopFile)) {
                Thread.sleep(500);
            }
            System.out.println("VipSecondInstance stopping");
        }
    }

    private static Map<String, Object> defaults() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.config.location", "classpath:/acceptance.yml");
        props.put("spring.profiles.active", "acceptance");
        props.put("server.address", "127.0.0.1");
        // 与测试实例一致的 servlet 容器与随机端口；实际端口经就绪文件交回脚本
        props.put("spring.main.web-application-type", "servlet");
        props.put("server.port", "0");
        return props;
    }
}
