package com.yuwen.visionspace.manager.outbound;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限地址判定：只做字面量解析，不发起任何网络请求，不探测真实内网或元数据。
 */
class OutboundAddressRulesTest {

    @Test
    void blocksLoopbackPrivateLinkLocalAndMetadata() throws Exception {
        assertTrue(OutboundAddressRules.isBlocked(address("127.0.0.1")));
        assertTrue(OutboundAddressRules.isBlocked(address("127.255.255.254")));
        assertTrue(OutboundAddressRules.isBlocked(address("10.1.2.3")));
        assertTrue(OutboundAddressRules.isBlocked(address("172.16.0.1")));
        assertTrue(OutboundAddressRules.isBlocked(address("172.31.255.255")));
        assertTrue(OutboundAddressRules.isBlocked(address("192.168.1.1")));
        // 云元数据端点
        assertTrue(OutboundAddressRules.isBlocked(address("169.254.169.254")));
        assertTrue(OutboundAddressRules.isBlocked(address("0.0.0.0")));
        assertTrue(OutboundAddressRules.isBlocked(address("100.64.0.1")));
        assertTrue(OutboundAddressRules.isBlocked(address("224.0.0.1")));
        assertTrue(OutboundAddressRules.isBlocked(address("255.255.255.255")));
    }

    @Test
    void blocksIpv6LoopbackUniqueLocalAndMappedV4() throws Exception {
        assertTrue(OutboundAddressRules.isBlocked(address("::1")));
        assertTrue(OutboundAddressRules.isBlocked(address("::")));
        assertTrue(OutboundAddressRules.isBlocked(address("fc00::1")));
        assertTrue(OutboundAddressRules.isBlocked(address("fd12:3456:789a::1")));
        assertTrue(OutboundAddressRules.isBlocked(address("fe80::1")));
        assertTrue(OutboundAddressRules.isBlocked(address("ff02::1")));
        // IPv4 映射与 6to4 内嵌内网地址必须解出内嵌部分再判
        assertTrue(OutboundAddressRules.isBlocked(address("::ffff:127.0.0.1")));
        assertTrue(OutboundAddressRules.isBlocked(address("::ffff:169.254.169.254")));
        assertTrue(OutboundAddressRules.isBlocked(address("2002:7f00:0001::1")));
    }

    @Test
    void allowsOrdinaryPublicAddresses() throws Exception {
        assertFalse(OutboundAddressRules.isBlocked(address("8.8.8.8")));
        assertFalse(OutboundAddressRules.isBlocked(address("203.0.114.10")));
        assertFalse(OutboundAddressRules.isBlocked(address("2606:4700:4700::1111")));
    }

    @Test
    void loopbackMayBeExplicitlyAllowedButNothingElseIs() throws Exception {
        assertFalse(OutboundAddressRules.isBlocked(address("127.0.0.1"), true));
        // 放行回环不等于放行内网与元数据
        assertTrue(OutboundAddressRules.isBlocked(address("10.0.0.1"), true));
        assertTrue(OutboundAddressRules.isBlocked(address("169.254.169.254"), true));
    }

    @Test
    void nullAddressIsBlocked() {
        assertTrue(OutboundAddressRules.isBlocked(null));
    }

    private InetAddress address(String literal) throws UnknownHostException {
        // getByName 对字面量不触发 DNS
        return InetAddress.getByName(literal);
    }
}
