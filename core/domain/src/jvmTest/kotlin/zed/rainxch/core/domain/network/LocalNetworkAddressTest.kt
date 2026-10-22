package zed.rainxch.core.domain.network

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkAddressTest {
    @Test
    fun privateLinkLocalCgnatMulticastAndBroadcastIpv4AreLocal() {
        listOf(
            "10.0.0.1",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.1.10",
            "169.254.10.20",
            "100.64.0.1",
            "100.127.255.254",
            "224.0.0.251",
            "239.255.255.250",
            "255.255.255.255",
        ).forEach { assertTrue(isLocal(it), it) }
    }

    @Test
    fun loopbackAndPublicIpv4AreNotLocal() {
        listOf(
            "127.0.0.1",
            "8.8.8.8",
            "140.82.112.3",
            "172.32.0.1",
            "172.15.255.255",
            "100.128.0.1",
            "192.169.0.1",
            "11.0.0.1",
        ).forEach { assertFalse(isLocal(it), it) }
    }

    @Test
    fun linkLocalMulticastAndUniqueLocalIpv6AreLocal() {
        listOf("fe80::1", "febf::1", "ff02::fb", "fd12:3456::1", "fc00::1").forEach { assertTrue(isLocal(it), it) }
    }

    @Test
    fun loopbackAndGlobalIpv6AreNotLocal() {
        listOf("::1", "2001:4860:4860::8888", "fec0::1", "fe00::1").forEach { assertFalse(isLocal(it), it) }
    }

    @Test
    fun malformedAddressesAreNotLocal() {
        assertFalse(isLocalNetworkAddress(byteArrayOf()))
        assertFalse(isLocalNetworkAddress(byteArrayOf(10, 0, 0)))
    }

    private fun isLocal(literal: String): Boolean = isLocalNetworkAddress(InetAddress.getByName(literal).address)
}
