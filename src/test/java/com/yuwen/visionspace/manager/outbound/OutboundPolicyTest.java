package com.yuwen.visionspace.manager.outbound;

import com.yuwen.visionspace.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出站策略的 URI 级判定。
 *
 * <p>全部使用字面量或不可解析的测试域名，不解析真实第三方、不访问内网。
 */
class OutboundPolicyTest {

    private OutboundPolicy policy(List<String> trustedHosts, boolean allowLoopback) {
        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        properties.setTrustedHosts(trustedHosts);
        properties.setAllowLoopback(allowLoopback);
        return new OutboundPolicy(properties);
    }

    @Test
    void rejectsNonHttpSchemes() {
        OutboundPolicy policy = policy(List.of(), false);
        for (String target : List.of("file:///etc/passwd", "ftp://example.com/a.jpg",
                "gopher://example.com/1", "jar:http://example.com/a.jar!/")) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> policy.requireAllowed(target));
            assertEquals(40000, error.getCode(), target);
        }
    }

    @Test
    void rejectsLiteralRestrictedAddresses() {
        OutboundPolicy policy = policy(List.of(), false);
        for (String target : List.of("http://127.0.0.1/a.png", "http://10.0.0.1/a.png",
                "http://169.254.169.254/latest/meta-data/", "http://[::1]/a.png",
                "http://[fd00::1]/a.png", "http://192.168.0.1/a.png")) {
            assertThrows(BusinessException.class, () -> policy.requireAllowed(target), target);
        }
    }

    @Test
    void rejectsCredentialsInUrlAndMalformedTargets() {
        OutboundPolicy policy = policy(List.of(), false);
        for (String target : List.of("http://user:pass@example.com/a.png", "http:///a.png",
                "not a url", "", "   ", "http://example.com/a b.png")) {
            assertThrows(BusinessException.class, () -> policy.requireAllowed(target), target);
        }
    }

    @Test
    void unresolvableHostFailsClosed() {
        OutboundPolicy policy = policy(List.of(), false);
        // .invalid 是保留域名，不会解析到任何真实地址
        BusinessException error = assertThrows(BusinessException.class,
                () -> policy.requireAllowed("http://nothing-here.invalid/a.png"));
        assertEquals(40000, error.getCode());
    }

    @Test
    void trustedHostsAreMatchedExactly() {
        OutboundPolicy policy = policy(List.of("mcp.internal.example"), false);
        assertTrue(policy.isTrusted("mcp.internal.example", 443));
        assertTrue(policy.isTrusted("MCP.Internal.Example.", 443));
        // 前缀、后缀与通配符都不能命中
        assertFalse(policy.isTrusted("mcp.internal.example.evil.com", 443));
        assertFalse(policy.isTrusted("evil-mcp.internal.example", 443));
        assertFalse(policy.isTrusted("internal.example", 443));
    }

    @Test
    void trustedHostOnlyOpensItsDeclaredPorts() {
        OutboundPolicy policy = policy(List.of("mcp.internal.example"), false);
        // 借可信主机名连到别的端口等于重开该主机上的任意服务
        assertFalse(policy.isTrusted("mcp.internal.example", 6379));
        assertThrows(BusinessException.class,
                () -> policy.requireAllowed("https://mcp.internal.example:6379/"));
        assertDoesNotThrow(() -> policy.requireAllowed("https://mcp.internal.example:8443/sse"));
    }

    @Test
    void trustedHostIsAllowedWithoutResolving() {
        // 受控内部依赖由运维声明；判定不依赖它当前能否解析
        OutboundPolicy policy = policy(List.of("mcp.internal.example"), false);
        assertDoesNotThrow(() -> policy.requireAllowed("https://mcp.internal.example/sse"));
        assertDoesNotThrow(() -> policy.assertConnectTarget(
                java.net.URI.create("https://mcp.internal.example/sse")));
    }

    @Test
    void loopbackIsOnlyAllowedWhenExplicitlyEnabled() {
        String target = "http://127.0.0.1:18080/image.png";
        assertThrows(BusinessException.class, () -> policy(List.of(), false).requireAllowed(target));
        assertDoesNotThrow(() -> policy(List.of(), true).requireAllowed(target));
        // 放行回环不会顺带放行元数据地址
        assertThrows(BusinessException.class,
                () -> policy(List.of(), true).requireAllowed("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void loopbackSwitchIsIgnoredInProduction() {
        // 一次误配的环境变量不能把整台机器的本地服务暴露给任意用户 URL
        OutboundPolicyProperties properties = new OutboundPolicyProperties();
        properties.setAllowLoopback(true);
        OutboundPolicy productionPolicy = new OutboundPolicy(properties, true);
        assertThrows(BusinessException.class,
                () -> productionPolicy.requireAllowed("http://127.0.0.1:8080/a.png"));
        // 同一份配置在非生产下照常放行，说明拒绝来自生产判定而不是开关失效
        assertDoesNotThrow(() -> new OutboundPolicy(properties, false)
                .requireAllowed("http://127.0.0.1:8080/a.png"));
    }

    @Test
    void defaultsAreBounded() {
        OutboundPolicy policy = policy(List.of(), false);
        assertTrue(policy.maxRedirects() > 0 && policy.maxRedirects() <= 10);
        assertTrue(policy.maxDownloadBytes() > 0);
        assertTrue(policy.totalTimeoutSeconds() > 0);
        assertTrue(policy.connectTimeoutSeconds() > 0);
    }
}
