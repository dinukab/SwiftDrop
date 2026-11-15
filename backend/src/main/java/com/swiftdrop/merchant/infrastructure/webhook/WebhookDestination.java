package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.apache.hc.client5.http.DnsResolver;

/** The same resolver supplies validated addresses directly to the HTTP connection manager. */
public final class WebhookDestination implements DnsResolver {
    @FunctionalInterface
    interface Lookup { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final boolean local;
    private final Set<String> deniedHosts;
    private final Lookup lookup;

    public WebhookDestination(boolean local, Set<String> deniedHosts) {
        this(local, deniedHosts, InetAddress::getAllByName);
    }

    WebhookDestination(boolean local, Set<String> deniedHosts, Lookup lookup) {
        this.local = local;
        this.deniedHosts = deniedHosts;
        this.lookup = lookup;
    }

    public URI validate(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (url.length() > 2048 || host == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || !uri.isAbsolute()) throw new Refused();
            boolean loopbackName = Set.of("localhost", "127.0.0.1", "[::1]", "::1")
                    .contains(host.toLowerCase(Locale.ROOT));
            boolean demo = local && loopbackName;
            if (!(uri.getScheme().equalsIgnoreCase("https")
                    || demo && uri.getScheme().equalsIgnoreCase("http"))
                    || !demo && uri.getPort() != -1 && uri.getPort() != 443) throw new Refused();
            resolve(host);
            return uri;
        } catch (IllegalArgumentException | UnknownHostException ex) {
            throw new Refused();
        }
    }

    @Override public InetAddress[] resolve(String host) throws UnknownHostException {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (deniedHosts.contains(normalized.replaceAll("\\.$", ""))) throw new Refused();
        InetAddress[] addresses = lookup.resolve(host);
        if (addresses.length == 0) throw new Refused();
        boolean demo = local && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(normalized);
        for (InetAddress address : addresses) {
            if (demo && address.isLoopbackAddress()) continue;
            if (prohibited(address.getAddress())) throw new Refused();
        }
        return addresses;
    }

    @Override public String resolveCanonicalHostname(String host) { return host; }

    static boolean prohibited(byte[] bytes) {
        if (bytes.length == 4) {
            long ip = 0;
            for (byte b : bytes) ip = (ip << 8) | (b & 255);
            long[][] ranges = {{0x00000000L,8},{0x0a000000L,8},{0x64400000L,10},
                    {0x7f000000L,8},{0xa9fe0000L,16},{0xac100000L,12},{0xc0000000L,24},
                    {0xc0000200L,24},{0xc0a80000L,16},{0xc6120000L,15},{0xc6336400L,24},
                    {0xcb007100L,24},{0xe0000000L,4},{0xf0000000L,4}};
            for (long[] range : ranges) {
                long mask = (0xffffffffL << (32 - range[1])) & 0xffffffffL;
                if ((ip & mask) == range[0]) return true;
            }
            return false;
        }
        if (bytes.length != 16) return true;
        int a = bytes[0] & 255, b = bytes[1] & 255;
        boolean zero96 = true;
        for (int i = 0; i < 12; i++) zero96 &= bytes[i] == 0;
        // Also refuse deprecated IPv4-compatible IPv6 forms.
        if (zero96) return true;
        if ((a & 254) == 252 || a == 255 || a == 254 && (b & 192) >= 128
                || a == 32 && b == 1 && bytes[2] == 13 && (bytes[3] & 255) == 184) return true;
        boolean mapped = true;
        for (int i = 0; i < 10; i++) mapped &= bytes[i] == 0;
        mapped &= (bytes[10] & 255) == 255 && (bytes[11] & 255) == 255;
        boolean nat64 = a == 0 && b == 100 && (bytes[2] & 255) == 255 && (bytes[3] & 255) == 155;
        for (int i = 4; i < 12; i++) nat64 &= bytes[i] == 0;
        if (mapped || nat64) return prohibited(Arrays.copyOfRange(bytes, 12, 16));
        if (a == 32 && b == 2) return prohibited(Arrays.copyOfRange(bytes, 2, 6));
        // Teredo and local-use translation prefixes can tunnel prohibited addresses.
        if (a == 32 && b == 1 && bytes[2] == 0 && bytes[3] == 0
                || a == 0 && b == 100 && (bytes[2] & 255) == 255 && (bytes[3] & 255) == 155) return true;
        return false;
    }

    public static final class Refused extends RuntimeException {
        public Refused() { super("Webhook destination refused"); }
    }
}
