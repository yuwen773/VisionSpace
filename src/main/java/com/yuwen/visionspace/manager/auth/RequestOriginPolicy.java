package com.yuwen.visionspace.manager.auth;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 请求来源判定。
 *
 * <p>CSRF 防护的信任基础是「浏览器不会让本站在没有显式授权的情况下带上凭据」，
 * 所以只有两类请求可以修改状态：
 * <ol>
 *   <li>没有 Origin 头的非浏览器请求（curl、服务端调用），它们不带跨站风险；</li>
 *   <li>Origin 与请求自身的 Host（含代理透传）同源的请求。</li>
 * </ol>
 * 配置里的可信 Origin 只额外放行开发期跨域入口，不能替代同源判定。
 *
 * <p>{@code Origin: null}（沙箱 iframe、部分重定向）按跨站处理，不做特殊放行。
 */
public final class RequestOriginPolicy {

    private RequestOriginPolicy() {
    }

    /**
     * @param origin      请求的 Origin 头，可为空
     * @param host        请求实际访问的 Host 头（可含端口）
     * @param forwardedHost 反向代理透传的原始 Host，可为空
     * @param requestScheme 请求实际使用的对外协议（已考虑反向代理），可为空
     * @param trustedOrigins 显式配置的可信来源
     * @return 该请求是否来自本站或显式可信来源
     */
    public static boolean isTrusted(String origin,
                                    String host,
                                    String forwardedHost,
                                    String requestScheme,
                                    List<String> trustedOrigins) {
        if (origin == null || origin.isBlank()) {
            // 没有 Origin：非浏览器请求，或浏览器不发送 Origin 的同源 GET；
            // 状态修改由双提交令牌把关，这里不额外拒绝。
            return true;
        }
        if (isTrustedOrigin(origin, host, forwardedHost, requestScheme)) {
            return true;
        }
        String normalized = normalize(origin);
        return trustedOrigins != null && trustedOrigins.stream()
                .map(RequestOriginPolicy::normalize)
                .anyMatch(normalized::equals);
    }

    /**
     * 请求自身的对外主机（含端口），作为同源判定的比较基准。
     *
     * <p>优先用反向代理透传的 {@code X-Forwarded-Host}；没有时用容器的服务器名与端口。
     * 生产启用了 {@code forward-headers-strategy: framework}，Spring 会把转发头
     * 还原成 {@code getServerName()/getServerPort()} 并从请求里移除原始头，
     * 因此这两种来源等价，读哪一个都不会退化。
     */
    public static String requestHost(String forwardedHost, String serverName, int serverPort) {
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            return forwardedHost.split(",")[0].trim();
        }
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        return serverPort > 0 ? serverName + ":" + serverPort : serverName;
    }

    /**
     * Origin 与请求自身的 Host、协议是否同源。
     */
    static boolean isTrustedOrigin(String origin, String host, String forwardedHost, String requestScheme) {
        URI uri = parse(origin);
        if (uri == null) {
            return false;
        }
        String candidateHost = firstNonBlank(forwardedHost, host);
        if (candidateHost == null) {
            return false;
        }
        if (!hostOnly(candidateHost).equalsIgnoreCase(uri.getHost() == null ? "" : uri.getHost())) {
            return false;
        }
        // 协议必须与浏览器实际使用的协议一致：https 页面不会被 http 来源冒用
        int originPort = effectivePort(uri.getScheme(), uri.getPort());
        int hostPort = portOf(candidateHost);
        String actualScheme = requestScheme == null || requestScheme.isBlank()
                ? schemeOfPort(hostPort)
                : requestScheme.trim().toLowerCase(Locale.ROOT);
        if (actualScheme == null) {
            return false;
        }
        if (!actualScheme.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        return effectivePort(actualScheme, hostPort) == originPort;
    }

    /**
     * 没有代理头或显式协议时，按端口推断：443 为 https，80/未声明为 http。
     */
    private static String schemeOfPort(int port) {
        return switch (port) {
            case 443 -> "https";
            case 80, -1 -> "http";
            default -> null;
        };
    }

    /**
     * 协议小写、去掉默认端口后的规范形式。
     */
    static String normalize(String value) {
        URI uri = parse(value);
        if (uri == null) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = effectivePort(scheme, uri.getPort());
        String suffix = port == defaultPort(scheme) ? "" : ":" + port;
        return scheme + "://" + host + suffix;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.getScheme() == null || uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.split(",")[0].trim();
        }
        return second == null ? null : second.trim();
    }

    private static String hostOnly(String hostAndPort) {
        String host = hostAndPort;
        int colon = host.startsWith("[") ? host.indexOf(':', host.indexOf(']')) : host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        return host;
    }

    private static int portOf(String hostAndPort) {
        int colon = hostAndPort.startsWith("[")
                ? hostAndPort.indexOf(':', hostAndPort.indexOf(']'))
                : hostAndPort.lastIndexOf(':');
        if (colon > 0 && hostAndPort.indexOf(':') == colon) {
            try {
                return Integer.parseInt(hostAndPort.substring(colon + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static int effectivePort(String scheme, int port) {
        return port > 0 ? port : defaultPort(scheme);
    }

    private static int defaultPort(String scheme) {
        String value = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "https", "wss" -> 443;
            case "http", "ws" -> 80;
            default -> -1;
        };
    }
}
