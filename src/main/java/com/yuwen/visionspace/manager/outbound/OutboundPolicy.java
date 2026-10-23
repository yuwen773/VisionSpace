package com.yuwen.visionspace.manager.outbound;

import com.yuwen.visionspace.config.ProductionProfiles;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户输入出站请求的唯一策略入口。
 *
 * <p>URL 图片下载与用户配置的远程 MCP 目标共用这一份判定，避免两处各写一套规则后
 * 其中一处被绕过。判定分两步：先看 URI 本身（协议、用户信息、主机字面量），
 * 再看主机解析出的每一个地址；任一步命中受限范围即拒绝，不做"部分放行"。
 *
 * <p>运维通过 {@code vision-space.outbound.trusted-hosts} 声明受控内部依赖；
 * 它按完整主机名精确匹配，只能由部署配置写入，用户请求无法影响，
 * 因此不会变成面向任意内部地址的通用例外。
 */
@Slf4j
@Component
public class OutboundPolicy {

    private final OutboundPolicyProperties properties;
    private final boolean production;

    public OutboundPolicy(OutboundPolicyProperties properties) {
        this(properties, false);
    }

    public OutboundPolicy(OutboundPolicyProperties properties, boolean production) {
        this.properties = properties;
        this.production = production;
    }

    /** Spring 装配：生产判定与启动守卫、MCP 能力封禁共用同一份规则。 */
    @org.springframework.beans.factory.annotation.Autowired
    public OutboundPolicy(OutboundPolicyProperties properties, Environment environment) {
        this(properties, ProductionProfiles.isProduction(environment.getActiveProfiles()));
    }

    /**
     * 回环放行开关：生产 profile 下即使被环境变量打开也不生效。
     *
     * <p>否则一次误配就会把整台机器的本地服务暴露给任意用户 URL。
     */
    private boolean loopbackAllowed() {
        return properties.isAllowLoopback() && !production;
    }

    /**
     * 校验并返回可安全使用的 URI。
     *
     * @throws BusinessException 目标不被允许时抛出，信息只描述原因、不回显解析结果
     */
    public URI requireAllowed(String rawTarget) {
        URI uri = parse(rawTarget);
        assertScheme(uri);
        assertNoUserInfo(uri);
        String host = requireHost(uri);
        if (isTrusted(host, effectivePort(uri))) {
            log.info("出站目标命中受控内部依赖: host={}", host);
            return uri;
        }
        if (isIpLiteral(host)) {
            if (OutboundAddressRules.isBlocked(literal(host), loopbackAllowed())) {
                throw denied("目标地址不在允许范围内");
            }
            return uri;
        }
        // 域名：解析结果必须全部可用，避免用多 A 记录混入内网地址
        requireAllowedAddresses(host, effectivePort(uri));
        return uri;
    }

    /**
     * 解析主机并逐条判定；返回已校验的地址列表。
     *
     * <p>任一解析结果为受限地址即整体拒绝：DNS 可以返回多个地址，
     * 只校验其中一个等于把另一个留给连接层。
     */
    public List<InetAddress> requireAllowedAddresses(String host, int port) {
        if (isTrusted(host, port)) {
            return List.of();
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw denied("目标主机无法解析");
        }
        if (resolved.length == 0) {
            throw denied("目标主机无法解析");
        }
        List<InetAddress> allowed = new ArrayList<>(resolved.length);
        for (InetAddress address : resolved) {
            if (OutboundAddressRules.isBlocked(address, loopbackAllowed())) {
                throw denied("目标地址不在允许范围内");
            }
            allowed.add(address);
        }
        return List.copyOf(allowed);
    }

    /**
     * 连接前对实际目标再做一次判定。
     *
     * <p>供传输层在每次建连/每跳重定向时调用，覆盖"创建客户端时校验过、
     * 但实际连接发生在之后"的时间差。
     */
    public void assertConnectTarget(URI uri) {
        String host = requireHost(uri);
        if (isTrusted(host, effectivePort(uri))) {
            return;
        }
        requireAllowedAddresses(host, effectivePort(uri));
    }

    /**
     * 供连接层在建立连接时解析并判定主机。
     *
     * <p>返回的地址即连接将要使用的地址，调用方不再自行解析，因此
     * 「判定结果」与「实际连接目标」是同一份数据。
     *
     * @param port 本次连接实际使用的端口，与主机一起决定是否命中受控依赖
     */
    public InetAddress[] resolveForConnection(String host, int port) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("empty host");
        }
        String normalized = normalizeHost(host);
        if (isTrusted(normalized, port)) {
            return InetAddress.getAllByName(normalized);
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(normalized);
        } catch (UnknownHostException e) {
            throw e;
        }
        if (resolved.length == 0) {
            throw new UnknownHostException(normalized);
        }
        for (InetAddress address : resolved) {
            if (OutboundAddressRules.isBlocked(address, loopbackAllowed())) {
                // 用策略异常表达拒绝：调用方据此失败，而不是发出连接
                throw new BlockedTargetException(denied("目标地址不在允许范围内"));
            }
        }
        return resolved;
    }

    /**
     * 连接层拒绝：保留业务错误码，供上层还原为明确的拒绝语义。
     */
    public static final class BlockedTargetException extends RuntimeException {
        private final BusinessException business;

        BlockedTargetException(BusinessException business) {
            super(business.getMessage(), business);
            this.business = business;
        }

        public BusinessException business() {
            return business;
        }
    }

    /**
     * 可信主机判定同时看端口：受控依赖只放行它实际暴露的端口。
     *
     * <p>只看主机名会让 {@code https://internal.example:6379/} 借道命中，
     * 等于把该主机上的任意服务重新暴露给用户输入。
     */
    public boolean isTrusted(String host, int port) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = normalizeHost(host);
        if (!trustedHosts().contains(normalized)) {
            return false;
        }
        return properties.getTrustedPorts().contains(port);
    }

    /** 实际连接端口；未显式给出时按协议默认端口。 */
    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public int maxRedirects() {
        return properties.getMaxRedirects();
    }

    public long maxDownloadBytes() {
        return properties.getMaxDownloadBytes();
    }

    public int totalTimeoutSeconds() {
        return properties.getTotalTimeoutSeconds();
    }

    public int connectTimeoutSeconds() {
        return properties.getConnectTimeoutSeconds();
    }

    public int readTimeoutSeconds() {
        return properties.getReadTimeoutSeconds();
    }

    public BusinessException denied(String reason) {
        // 统一错误：不区分"被策略拒绝"和"无法解析"，避免用错误信息探测内网
        return new BusinessException(ErrorCode.PARAMS_ERROR, reason);
    }

    private URI parse(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw denied("出站地址为空");
        }
        String value = rawTarget.trim();
        if (value.chars().anyMatch(Character::isISOControl) || value.chars().anyMatch(Character::isWhitespace)) {
            throw denied("出站地址格式不正确");
        }
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null) {
                throw denied("出站地址格式不正确");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw denied("出站地址格式不正确");
        }
    }

    private void assertScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw denied("仅支持 HTTP 或 HTTPS 协议");
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        if (!properties.getAllowedSchemes().contains(normalized)) {
            throw denied("仅支持 HTTP 或 HTTPS 协议");
        }
    }

    private void assertNoUserInfo(URI uri) {
        // user@host 会让日志与代理看到的主机与真实连接目标不一致
        if (uri.getRawUserInfo() != null) {
            throw denied("出站地址格式不正确");
        }
    }

    private String requireHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw denied("出站地址格式不正确");
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw denied("出站地址格式不正确");
        }
        return host;
    }

    /**
     * 字面量主机名转地址；解析失败按拒绝处理。
     */
    private InetAddress literal(String host) {
        try {
            return InetAddress.getByName(normalizeHost(host));
        } catch (UnknownHostException e) {
            throw denied("目标地址不在允许范围内");
        }
    }

    private boolean isIpLiteral(String host) {
        String normalized = normalizeHost(host);
        if (normalized.contains(":")) {
            return true;
        }
        return normalized.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private String normalizeHost(String host) {
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        // URI.getHost() 已去掉末尾的点；这里再兜底一次，避免 FQDN 写法绕过精确匹配
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Set<String> trustedHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        for (String configured : properties.getTrustedHosts()) {
            if (configured != null && !configured.isBlank()) {
                hosts.add(normalizeHost(configured));
            }
        }
        return hosts;
    }
}
