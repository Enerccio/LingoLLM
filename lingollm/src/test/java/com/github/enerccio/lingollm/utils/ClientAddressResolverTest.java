package com.github.enerccio.lingollm.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client address is the one of the connection, {@code X-Forwarded-For} only counts when it comes from a trusted
 * proxy.
 */
class ClientAddressResolverTest {

    @Test
    void headerIsIgnoredWithoutTrustedProxies() {
        ClientAddressResolver resolver = new ClientAddressResolver("");
        assertThat(resolver.hasTrustedProxies()).isFalse();
        assertThat(resolver.resolve("192.168.1.5", "6.6.6.6")).isEqualTo("192.168.1.5");
        assertThat(resolver.resolve("127.0.0.1", "6.6.6.6")).isEqualTo("127.0.0.1");
    }

    @Test
    void privateNetworkIsNotTrustedByItself() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        // a client on the LAN can't pick its address
        assertThat(resolver.resolve("192.168.1.5", "6.6.6.6")).isEqualTo("192.168.1.5");
        assertThat(resolver.resolve("172.17.0.1", "6.6.6.6")).isEqualTo("172.17.0.1");
    }

    @Test
    void headerIsReadFromTrustedProxy() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve("127.0.0.1", null)).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", " ")).isEqualTo("127.0.0.1");
    }

    @Test
    void entriesLeftOfTheProxyAreClientControlled() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        // the client sent "1.1.1.1", the proxy appended what it saw
        assertThat(resolver.resolve("127.0.0.1", "1.1.1.1, 203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void chainOfProxiesIsWalkedFromTheRight() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1, 10.0.0.0/8, fd00::/8");
        assertThat(resolver.resolve("127.0.0.1", "203.0.113.7, 10.1.2.3, 10.4.5.6")).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve("10.9.9.9", "203.0.113.7, fd00::1")).isEqualTo("203.0.113.7");
        // all hops are ours: the connection is the best we know
        assertThat(resolver.resolve("127.0.0.1", "10.1.2.3")).isEqualTo("127.0.0.1");
    }

    @Test
    void cidrRangesMatchOnPrefixBits() {
        ClientAddressResolver resolver = new ClientAddressResolver("172.16.0.0/12, 2001:db8::/32");
        assertThat(resolver.isTrusted("172.16.0.1")).isTrue();
        assertThat(resolver.isTrusted("172.31.255.255")).isTrue();
        assertThat(resolver.isTrusted("172.32.0.1")).isFalse();
        assertThat(resolver.isTrusted("2001:db8:1::1")).isTrue();
        assertThat(resolver.isTrusted("2001:db9::1")).isFalse();
        // an IPv4 range never matches an IPv6 address and the other way round
        assertThat(resolver.isTrusted("::1")).isFalse();
    }

    @Test
    void hostNamesAreNeverResolved() {
        ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1");
        assertThat(resolver.isTrusted("localhost")).isFalse();
        assertThat(resolver.resolve("localhost", "203.0.113.7")).isEqualTo("localhost");
        assertThat(resolver.resolve("127.0.0.1", "not-an-address")).isEqualTo("not-an-address");
    }

    @Test
    void badConfigurationIsRejected() {
        assertThatThrownBy(() -> new ClientAddressResolver("proxy.example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddressResolver("10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddressResolver("10.0.0.0/x")).isInstanceOf(IllegalArgumentException.class);
    }
}
