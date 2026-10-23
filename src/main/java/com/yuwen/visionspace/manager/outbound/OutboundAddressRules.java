package com.yuwen.visionspace.manager.outbound;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * 用户可控目标不允许连接的地址范围。
 *
 * <p>这是纯判定，不做任何网络操作：调用方拿到解析结果后逐条判定，任一条命中即拒绝。
 * 覆盖回环、私网、链路本地、云元数据、保留/文档/组播等地址，IPv4 与 IPv6 都按同一口径处理；
 * IPv4 映射与 NAT64/6to4 这类内嵌 IPv4 的 IPv6 地址会解出内嵌地址再判一次，避免绕过。
 */
public final class OutboundAddressRules {

    private OutboundAddressRules() {
    }

    /**
     * 该地址是否属于用户输入不得访问的范围。
     */
    public static boolean isBlocked(InetAddress address) {
        return isBlocked(address, false);
    }

    /**
     * 同上；{@code allowLoopback} 只放行回环，其余受限范围仍然拒绝。
     *
     * <p>仅用于受控验收环境连接本机替身。生产配置不打开它，
     * 因此不会成为访问内网或元数据的通用例外。
     */
    public static boolean isBlocked(InetAddress address, boolean allowLoopback) {
        if (address == null) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address.isLoopbackAddress()) {
            return !allowLoopback;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isBlockedV4(bytes);
        }
        if (address instanceof Inet6Address) {
            return isBlockedV6(bytes);
        }
        // 未知实现按拒绝处理
        return true;
    }

    private static boolean isBlockedV4(byte[] b) {
        int a0 = b[0] & 0xFF;
        int a1 = b[1] & 0xFF;
        int a2 = b[2] & 0xFF;
        int a3 = b[3] & 0xFF;
        // 0.0.0.0/8 "本网络"
        if (a0 == 0) {
            return true;
        }
        // 10.0.0.0/8
        if (a0 == 10) {
            return true;
        }
        // 100.64.0.0/10 运营商级 NAT
        if (a0 == 100 && a1 >= 64 && a1 <= 127) {
            return true;
        }
        // 127.0.0.0/8
        if (a0 == 127) {
            return true;
        }
        // 169.254.0.0/16 链路本地（含 169.254.169.254 元数据）
        if (a0 == 169 && a1 == 254) {
            return true;
        }
        // 172.16.0.0/12
        if (a0 == 172 && a1 >= 16 && a1 <= 31) {
            return true;
        }
        // 192.0.0.0/24 IETF 协议分配
        if (a0 == 192 && a1 == 0 && a2 == 0) {
            return true;
        }
        // 192.0.2.0/24 文档
        if (a0 == 192 && a1 == 0 && a2 == 2) {
            return true;
        }
        // 192.168.0.0/16
        if (a0 == 192 && a1 == 168) {
            return true;
        }
        // 198.18.0.0/15 基准测试
        if (a0 == 198 && (a1 == 18 || a1 == 19)) {
            return true;
        }
        // 198.51.100.0/24 文档
        if (a0 == 198 && a1 == 51 && a2 == 100) {
            return true;
        }
        // 203.0.113.0/24 文档
        if (a0 == 203 && a1 == 0 && a2 == 113) {
            return true;
        }
        // 224.0.0.0/4 组播
        if (a0 >= 224 && a0 <= 239) {
            return true;
        }
        // 240.0.0.0/4 保留（含 255.255.255.255 广播）
        return a0 >= 240;
    }

    private static boolean isBlockedV6(byte[] b) {
        // ::ffff:a.b.c.d IPv4 映射
        if (isMappedV4(b)) {
            return isBlockedV4(new byte[]{b[12], b[13], b[14], b[15]});
        }
        // 64:ff9b::/96 NAT64
        if (matches(b, new int[]{0x00, 0x64, 0xff, 0x9b}, 0, 4) && allZero(b, 4, 12)) {
            return isBlockedV4(new byte[]{b[12], b[13], b[14], b[15]});
        }
        // 2002::/16 6to4：内嵌 IPv4 在 2..6
        if (matches(b, new int[]{0x20, 0x02}, 0, 2)) {
            return isBlockedV4(new byte[]{b[2], b[3], b[4], b[5]});
        }
        // fc00::/7 唯一本地
        if ((b[0] & 0xFE) == 0xFC) {
            return true;
        }
        // fe80::/10 链路本地
        if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) {
            return true;
        }
        // ff00::/8 组播
        if ((b[0] & 0xFF) == 0xFF) {
            return true;
        }
        // 2001:db8::/32 文档
        if (matches(b, new int[]{0x20, 0x01, 0x0d, 0xb8}, 0, 4)) {
            return true;
        }
        // 100::/64 丢弃前缀
        if (matches(b, new int[]{0x01, 0x00}, 0, 2) && allZero(b, 2, 8)) {
            return true;
        }
        return false;
    }

    private static boolean isMappedV4(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }

    private static boolean matches(byte[] bytes, int[] expected, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
