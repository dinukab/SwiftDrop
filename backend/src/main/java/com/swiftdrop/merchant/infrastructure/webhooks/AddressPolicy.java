package com.swiftdrop.merchant.infrastructure.webhooks;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Which addresses a webhook may be sent to (ADR 0004 section 7.3). Blocks everything that is not a
 * public unicast address: loopback, private, link-local (including the cloud metadata address),
 * carrier-grade NAT, multicast, reserved and unspecified ranges, for IPv4 and IPv6.
 */
public final class AddressPolicy {

    private AddressPolicy() {
    }

    /** Whether an IP literal from the URL is public. A name is not checked here; the resolver does that. */
    public static boolean isLiteralPublic(String literal) {
        try {
            return isPublic(InetAddress.getByName(literal));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            return isPublicIpv4(b);
        }
        // IPv4-mapped IPv6 (::ffff:a.b.c.d) is judged by the IPv4 address inside it
        if (isIpv4Mapped(b)) {
            return isPublicIpv4(new byte[] {b[12], b[13], b[14], b[15]});
        }
        // Unique local addresses fc00::/7
        return (b[0] & 0xFE) != 0xFC;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
    }

    private static boolean isPublicIpv4(byte[] b) {
        int a = b[0] & 0xFF;
        int c = b[1] & 0xFF;
        if (a == 0) {
            return false; // 0.0.0.0/8 "this network"
        }
        if (a == 100 && (c & 0xC0) == 0x40) {
            return false; // 100.64.0.0/10 carrier-grade NAT
        }
        if (a >= 240) {
            return false; // 240.0.0.0/4 reserved, including 255.255.255.255
        }
        return true;
    }
}
