package com.github.enerccio.lingollm.utils;

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the address of the client of a request. The address the connection came from is used, unless that is a
 * trusted proxy: then {@code X-Forwarded-For} is read from the right and the first address that is not a trusted
 * proxy itself is the client. Everything left of it, and the header from any other peer, is client controlled and
 * ignored. With no trusted proxies (the default) the header is never read.
 */
public class ClientAddressResolver {

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+(%[\\w.]+)?");

    private final List<Network> trusted = new ArrayList<>();

    /**
     * @param trustedProxies comma separated addresses or CIDR ranges ({@code 127.0.0.1, 10.0.0.0/8, fd00::/8}), may be
     *                       blank
     * @throws IllegalArgumentException when an entry is not an address or range
     */
    public ClientAddressResolver(String trustedProxies) {
        for (String entry : StringUtils.split(StringUtils.defaultString(trustedProxies), ',')) {
            if (StringUtils.isNotBlank(entry)) {
                trusted.add(Network.parse(entry.trim()));
            }
        }
    }

    public boolean hasTrustedProxies() {
        return !trusted.isEmpty();
    }

    public boolean isTrusted(String address) {
        byte[] bytes = literal(address);
        return bytes != null && trusted.stream().anyMatch(n -> n.contains(bytes));
    }

    /**
     * @param remoteAddress  address of the peer of the connection
     * @param forwardedFor   value of the {@code X-Forwarded-For} header, may be null
     */
    public String resolve(String remoteAddress, String forwardedFor) {
        if (StringUtils.isBlank(forwardedFor) || !isTrusted(remoteAddress)) {
            return remoteAddress;
        }
        String[] chain = forwardedFor.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String candidate = chain[i].trim();
            if (!candidate.isEmpty() && !isTrusted(candidate)) {
                return candidate;
            }
        }
        // every hop is one of ours, the connection itself is the best answer
        return remoteAddress;
    }

    /** Bytes of an IP literal, null for anything else (a host name is never resolved). */
    private static byte[] literal(String address) {
        if (address == null || !IP_LITERAL.matcher(address.trim()).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(address.trim()).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private record Network(byte[] prefix, int bits) {

        static Network parse(String entry) {
            String address = StringUtils.substringBefore(entry, "/");
            byte[] bytes = literal(address);
            if (bytes == null) {
                throw new IllegalArgumentException("Not an IP address or range: " + entry);
            }
            int bits = bytes.length * 8;
            if (entry.contains("/")) {
                try {
                    bits = Integer.parseInt(StringUtils.substringAfter(entry, "/").trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad prefix length in " + entry);
                }
                if (bits < 0 || bits > bytes.length * 8) {
                    throw new IllegalArgumentException("Bad prefix length in " + entry);
                }
            }
            return new Network(bytes, bits);
        }

        boolean contains(byte[] address) {
            if (address.length != prefix.length) {
                return false;
            }
            int full = bits / 8;
            for (int i = 0; i < full; i++) {
                if (address[i] != prefix[i]) {
                    return false;
                }
            }
            int rest = bits % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xff << (8 - rest)) & 0xff;
            return (address[full] & mask) == (prefix[full] & mask);
        }
    }
}
