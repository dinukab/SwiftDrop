package com.swiftdrop.merchant.infrastructure.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "93.184.216.34", "1.1.1.1", "2606:4700:4700::1111"})
    void publicAddressesAreAllowed(String address) throws Exception {
        assertThat(AddressPolicy.isPublic(InetAddress.getByName(address))).as(address).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "240.0.0.1",
            "255.255.255.255",
            "224.0.0.1",
            "::1",
            "::",
            "fe80::1",
            "fc00::1",
            "fd12:3456::1",
            "ff02::1",
            "::ffff:10.0.0.1",
            "::ffff:127.0.0.1"})
    void privateLoopbackLinkLocalAndReservedAddressesAreRefused(String address) throws Exception {
        assertThat(AddressPolicy.isPublic(InetAddress.getByName(address))).as(address).isFalse();
    }

    @Test
    void anIpLiteralIsCheckedWithoutAnyDnsLookup() {
        assertThat(AddressPolicy.isLiteralPublic("8.8.8.8")).isTrue();
        assertThat(AddressPolicy.isLiteralPublic("10.20.30.40")).isFalse();
    }
}
