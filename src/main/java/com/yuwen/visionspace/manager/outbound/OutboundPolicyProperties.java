package com.yuwen.visionspace.manager.outbound;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户输入出站请求的边界配置。
 *
 * <p>回环、私网、链路本地、元数据等受限地址一律内置拒绝，不通过配置放宽；
 * 这里只暴露有界参数，以及一个仅由运维填写、按完整主机名精确匹配的可信主机列表，
 * 用于区分「受控部署的内部依赖」和「用户任意目标」。
 *
 * <p>{@code allow-loopback} 只服务于本机替身，生产 profile 下由
 * {@link OutboundPolicy} 强制忽略：否则一次误配就会把整台机器的本地服务暴露给任意用户 URL。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.outbound")
public class OutboundPolicyProperties {

    /**
     * 允许的出站协议。只支持 http/https，其他协议（file、ftp、gopher 等）一律拒绝。
     */
    private List<String> allowedSchemes = new ArrayList<>(List.of("http", "https"));

    /**
     * 运维配置的可信主机：完整主机名精确匹配（不含通配符），
     * 允许其解析到内部地址。用户输入不会写入这里。
     */
    private List<String> trustedHosts = new ArrayList<>();

    /**
     * 可信主机上允许连接的端口。
     *
     * <p>受控依赖通常只暴露一个 HTTPS 端口；不限制端口会让
     * {@code https://internal.example:6379/} 这类地址借可信主机名连到别的服务。
     */
    private List<Integer> trustedPorts = new ArrayList<>(List.of(443, 8443));

    /**
     * 测试/本地替身专用：允许访问回环地址。生产 profile 下不生效（见 {@link OutboundPolicy}）。
     */
    private boolean allowLoopback = false;

    /** 单次下载允许的最大重定向次数。 */
    private int maxRedirects = 3;

    /** 单次下载允许的最大响应字节数。 */
    private long maxDownloadBytes = 2L * 1024 * 1024;

    /** 单次下载（含重定向与正文）的总时间上限，单位秒。 */
    private int totalTimeoutSeconds = 10;

    /** 建立连接的时间上限，单位秒。 */
    private int connectTimeoutSeconds = 5;

    /** 读取响应头与正文的单次阻塞上限，单位秒。 */
    private int readTimeoutSeconds = 5;
}
