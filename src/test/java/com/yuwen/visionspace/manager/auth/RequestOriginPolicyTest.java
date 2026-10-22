package com.yuwen.visionspace.manager.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 来源判定：同源放行、显式可信来源放行、其余拒绝；代理头不能成为绕过手段。
 */
class RequestOriginPolicyTest {

    private static final List<String> TRUSTED = List.of("http://localhost:5173", "https://visionspace.space");

    @Test
    void missingOriginIsAllowed() {
        assertTrue(trusted(null, "visionspace.space", null, "https"));
        assertTrue(trusted("  ", "visionspace.space", null, "https"));
    }

    @Test
    void sameOriginMatchesHostAndPort() {
        assertTrue(trusted("https://visionspace.space", "visionspace.space", null, "https"));
        assertTrue(trusted("https://visionspace.space", "visionspace.space:443", null, "https"));
        assertTrue(trusted("http://127.0.0.1:8081", "127.0.0.1:8081", null, "http"));
    }

    @Test
    void proxyHeadersDefineTheOriginalEntry() {
        assertTrue(trusted("https://visionspace.space", "127.0.0.1:8081",
                "visionspace.space", "https"));
        assertFalse(trusted("https://internal.example", "127.0.0.1:8081", null, "http"),
                "没有代理头时后端看到的 Host 仍不同源");
    }

    @Test
    void schemeMismatchIsCrossOrigin() {
        assertFalse(trusted("http://visionspace.space", "visionspace.space", null, "https"),
                "https 页面不能被 http 来源冒用");
        assertFalse(trusted("https://internal.example", "127.0.0.1:8081", "internal.example", "http"));
        assertFalse(trusted("https://internal.example", "internal.example:8443", null, "https"));
    }

    @Test
    void explicitTrustedOriginIsAllowed() {
        assertTrue(trusted("http://localhost:5173", "127.0.0.1:8081", null, "http"));
        assertTrue(trusted("https://visionspace.space", "127.0.0.1:8081", null, "http"));
    }

    @Test
    void lookalikeOriginsAreRejected() {
        assertFalse(trusted("https://evil.example", "visionspace.space", null, "https"));
        assertFalse(trusted("https://visionspace.space.evil.example", "visionspace.space", null, "https"));
        assertFalse(trusted("null", "visionspace.space", null, "https"), "沙箱来源按跨站处理");
    }

    @Test
    void normalizationIgnoresCaseAndDefaultPort() {
        assertTrue(trusted("HTTPS://VisionSpace.Space:443", "visionspace.space", null, "https"));
    }

    @Test
    void malformedProxyAndOriginValuesDoNotThrow() {
        assertFalse(trusted("not a url", "visionspace.space", null, "https"));
        assertTrue(trusted(null, null, null, "https"), "缺失 Origin 的请求没有可借用的浏览器来源");
    }

    @Test
    void requestHostPrefersForwardedHostAndFallsBackToContainerHost() {
        // 代理透传的原始入口优先
        assertEquals("visionspace.space", RequestOriginPolicy.requestHost("visionspace.space", "127.0.0.1", 8081));
        // 多级代理会追加多个值，取最外层（第一个）
        assertEquals("a.example", RequestOriginPolicy.requestHost("a.example, b.example", "127.0.0.1", 8081));
        // 没有转发头时用框架还原后的服务器名与端口
        assertEquals("visionspace.space:8443", RequestOriginPolicy.requestHost(null, "visionspace.space", 8443));
        assertEquals("visionspace.space", RequestOriginPolicy.requestHost("", "visionspace.space", -1));
        assertNull(RequestOriginPolicy.requestHost(null, null, 8081), "无法确定入口时宁可拒绝");
    }

    private boolean trusted(String origin, String host, String forwardedHost, String requestScheme) {
        return RequestOriginPolicy.isTrusted(origin, host, forwardedHost, requestScheme, TRUSTED);
    }
}
