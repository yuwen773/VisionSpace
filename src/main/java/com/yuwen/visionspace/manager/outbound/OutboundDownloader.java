package com.yuwen.visionspace.manager.outbound;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 有界的用户 URL 下载。
 *
 * <p>下载不依赖 HEAD：内容类型、长度与地址都在实际 GET 上判定，因此 HEAD 不被支持
 * 或返回异常都不会跳过安全检查。DNS 由解析器在建立连接的那一刻判定并固定，
 * 重定向由本类逐跳处理，每一跳重新解析、重新判定；连接与读取都有时间上限，
 * 正文按实际读到的字节数截断，不采信 Content-Length。
 */
@Slf4j
@Component
public class OutboundDownloader {

    private static final int MAX_REDIRECTS_ABSOLUTE = 10;

    private final OutboundPolicy policy;

    public OutboundDownloader(OutboundPolicy policy) {
        this.policy = policy;
    }

    /**
     * 下载到本地文件，返回实际写入的字节数与内容类型。
     *
     * @throws BusinessException 目标不被允许、超出上限或响应不可用时抛出；
     *                           调用方负责清理已创建的临时文件
     */
    public Downloaded download(String rawUrl, File target, List<String> allowedContentTypes) {
        long limit = policy.maxDownloadBytes();
        Result<Long> result = fetch(rawUrl, allowedContentTypes,
                (content, deadline) -> copyBounded(content, target, limit, deadline));
        return new Downloaded(result.payload(), result.contentType(), result.finalUri());
    }

    /**
     * 读到内存，用于服务端记录里保存的存量地址。
     *
     * <p>与 {@link #download} 共用同一套出站判定与逐跳重定向规则，差别只在落点：
     * 这里不落盘。大小上限由调用方给出——存量资源的上限来自内容读取边界，
     * 不是用户 URL 的下载上限，因此不套用 {@code max-download-bytes}。
     *
     * @param maxBytes 允许读入内存的最大字节数
     * @return 实际内容
     */
    public byte[] readToMemory(String rawUrl, long maxBytes, List<String> allowedContentTypes) {
        Result<byte[]> result = fetch(rawUrl, allowedContentTypes,
                (content, deadline) -> readBounded(content, maxBytes, deadline));
        return result.payload();
    }

    /**
     * 逐跳执行请求；正文交给 {@code reader} 决定落在哪里。
     *
     * <p>重定向、状态码、内容类型与超时都在这里判定，两个公开入口只是换了正文处理方式。
     */
    private <T> Result<T> fetch(String rawUrl, List<String> allowedContentTypes, BodyReader<T> reader) {
        long deadline = System.nanoTime() + policy.totalTimeoutSeconds() * 1_000_000_000L;
        URI current = policy.requireAllowed(rawUrl).normalize();
        int redirects = 0;

        try {
            while (true) {
                if (System.nanoTime() > deadline) {
                    throw policy.denied("下载超时");
                }
                // 每一跳单独建客户端：解析器绑定到这一跳的端口，
                // 「判定用的地址」与「实际连接的地址」因此是同一份解析结果
                try (CloseableHttpClient client = buildClient(portOf(current));
                     CloseableHttpResponse response = client.execute(request(current))) {
                    int status = response.getStatusLine().getStatusCode();
                    if (isRedirect(status)) {
                        String location = header(response, "Location");
                        if (location == null || location.isBlank()) {
                            throw policy.denied("重定向缺少目标地址");
                        }
                        if (++redirects > Math.min(policy.maxRedirects(), MAX_REDIRECTS_ABSOLUTE)) {
                            throw policy.denied("重定向次数超出上限");
                        }
                        // 每一跳都重新解析并重新判定，不沿用上一跳的结论
                        current = current.resolve(location.trim()).normalize();
                        policy.requireAllowed(current.toString());
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw policy.denied("文件无法访问");
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw policy.denied("文件内容为空");
                    }
                    String contentType = header(response, "Content-Type");
                    assertContentType(contentType, allowedContentTypes);
                    try (InputStream content = entity.getContent()) {
                        return new Result<>(reader.read(content, deadline), contentType, current);
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            // 解析器抛出的策略拒绝会被 HttpClient 包成 IOException，这里还原业务语义
            OutboundPolicy.BlockedTargetException blocked = findBlocked(e);
            if (blocked != null) {
                throw blocked.business();
            }
            log.warn("用户 URL 下载失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件下载失败");
        }
    }

    /** 正文处理：落到文件或读进内存，超时与大小上限由实现自己执行。 */
    @FunctionalInterface
    private interface BodyReader<T> {
        T read(InputStream content, long deadline) throws IOException;
    }

    /** 一次请求的结果：正文处理产物加上响应的类型与最终地址。 */
    private record Result<T>(T payload, String contentType, URI finalUri) {
    }

    private HttpGet request(URI target) {
        HttpGet request = new HttpGet(target);
        request.setHeader(HttpHeaders.ACCEPT, "image/*");
        return request;
    }

    private int portOf(URI target) {
        if (target.getPort() != -1) {
            return target.getPort();
        }
        return "https".equalsIgnoreCase(target.getScheme()) ? 443 : 80;
    }

    private OutboundPolicy.BlockedTargetException findBlocked(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof OutboundPolicy.BlockedTargetException blocked) {
                return blocked;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /**
     * 只做地址与协议校验，不发起任何网络请求。
     */
    public void assertAllowed(String rawUrl) {
        policy.requireAllowed(rawUrl);
    }

    private CloseableHttpClient buildClient(int port) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(policy.connectTimeoutSeconds() * 1000)
                .setConnectionRequestTimeout(policy.connectTimeoutSeconds() * 1000)
                .setSocketTimeout(policy.readTimeoutSeconds() * 1000)
                // 重定向由本类逐跳处理，客户端不得自行跟随
                .setRedirectsEnabled(false)
                .build();
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(policy.readTimeoutSeconds() * 1000)
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setDefaultSocketConfig(socketConfig)
                .setDnsResolver(new PinningResolver(policy, port))
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    private void assertContentType(String contentType, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        // 未声明类型时不做放行判断，交由后续图片解码确认；声明了就必须匹配
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
        }
    }

    private long copyBounded(InputStream in, File target, long limit, long deadline) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream out = java.nio.file.Files.newOutputStream(target.toPath())) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                // 以实际读到的字节为准，Content-Length 只是提示，不能作为放行依据
                if (total > limit) {
                    throw policy.denied("文件大小不能超过 " + limitFor(limit));
                }
                if (System.nanoTime() > deadline) {
                    throw policy.denied("下载超时");
                }
                out.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw policy.denied("文件内容为空");
        }
        return total;
    }

    /**
     * 读进内存的正文处理：边界与落盘版一致，只是不产生临时文件。
     */
    private byte[] readBounded(InputStream in, long limit, long deadline) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        long total = 0;
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > limit) {
                throw policy.denied("文件大小不能超过 " + limitFor(limit));
            }
            if (System.nanoTime() > deadline) {
                throw policy.denied("下载超时");
            }
            buffer.write(chunk, 0, read);
        }
        if (total == 0) {
            throw policy.denied("文件内容为空");
        }
        return buffer.toByteArray();
    }

    /** 上限的两种量级：按 MB 给出更直观，小于 1MB 时直接报字节数。 */
    private static String limitFor(long limit) {
        return limit >= 1024 * 1024 ? (limit / (1024 * 1024)) + "MB" : limit + " 字节";
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private String header(CloseableHttpResponse response, String name) {
        var header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    /**
     * 建连时判定并固定地址：既完成策略校验，也让连接使用刚校验过的结果。
     */
    static final class PinningResolver implements DnsResolver {

        private final OutboundPolicy policy;
        private final int port;

        PinningResolver(OutboundPolicy policy, int port) {
            this.policy = policy;
            this.port = port;
        }

        @Override
        public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
            // 判定与连接使用同一份解析结果：这里返回什么，连接就用什么
            return policy.resolveForConnection(host, port);
        }
    }

    /**
     * 下载结果。
     *
     * @param bytes       实际写入的字节数
     * @param contentType 响应声明的内容类型（可能为空）
     * @param finalUri    最终生效的地址（重定向后）
     */
    public record Downloaded(long bytes, String contentType, URI finalUri) {
    }
}
