package com.yuwen.visionspace.agent.mcp;

import com.yuwen.visionspace.config.ProductionProfiles;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * MCP 危险能力的唯一判定入口。
 *
 * <p>用户自定义 STDIO 会在服务端启动任意进程，生产环境一律拒绝：从配置校验、
 * 客户端创建、工具发现到实际执行，每个能启动进程的入口都先问这里，
 * 拒绝发生在创建进程之前，因此拒绝路径没有任何进程启动。
 *
 * <p>生产判定只看显式激活的 profile，与 {@code ProductionSecretsGuard} 保持一致；
 * 重新开放需要独立的隔离设计，不通过配置开关绕回。
 */
@Slf4j
@Component
public class McpCapabilityGuard {

    private final boolean production;

    public McpCapabilityGuard(Environment environment) {
        this.production = isProduction(environment.getActiveProfiles());
    }

    /** 生产环境下用户自定义 STDIO 被禁用。 */
    public boolean stdioDisabled() {
        return production;
    }

    /**
     * 拒绝在禁用环境下创建 STDIO 客户端或启动进程。
     *
     * @param context 调用点，仅用于日志定位，不写入用户可见错误
     */
    public void assertStdioAllowed(String context) {
        if (production) {
            log.warn("生产环境拒绝用户自定义 STDIO MCP，调用点: {}", context);
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,
                    "生产环境不支持自定义 STDIO MCP 服务，请改用远程 HTTP/SSE 服务");
        }
    }

    /**
     * 用户可见的能力描述：UI 与服务端用同一份判定，避免界面宣称被禁能力可用。
     */
    public boolean isInstallTypeAllowed(String installType) {
        return !production || !"STDIO".equalsIgnoreCase(installType);
    }

    /**
     * 声明是否为 STDIO。
     *
     * <p>只信 installType 会被绕过：客户端可以声明 SSE，却在部署配置里写 command。
     * 任一证据指向本地进程即按 STDIO 处理。配置保存与客户端创建共用这一份判定。
     */
    public static boolean isStdio(String installType, String command) {
        if (installType != null && "STDIO".equalsIgnoreCase(installType.trim())) {
            return true;
        }
        return command != null && !command.isBlank();
    }

    /**
     * 按「声明的类型 + 配置里的 command」判定并拒绝被禁能力。
     *
     * @return 是否按 STDIO 处理（调用方据此选择传输方式）
     */
    public boolean assertStdioAllowedIfDeclared(String installType, String command, String context) {
        if (isStdio(installType, command)) {
            assertStdioAllowed(context);
            return true;
        }
        return false;
    }

    static boolean isProduction(String[] activeProfiles) {
        return ProductionProfiles.isProduction(activeProfiles);
    }
}
