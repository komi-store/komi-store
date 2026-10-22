package zed.rainxch.core.domain.network

fun isLocalNetworkAddress(address: ByteArray): Boolean = LOCAL_NETWORK_RANGES.any { it.contains(address) }

private const val BITS_PER_BYTE = 8
private const val HIGH_BIT = 0x80
private const val IPV4_BYTES = 4
private const val IPV6_BYTES = 16

// Android 17's local network definition (developer.android.com/privacy-and-security/local-network-definition).
// It also counts any directly connected IPv6 route, which an address alone can't show; unique-local fc00::/7 is
// the part that can.
private val LOCAL_NETWORK_RANGES =
    listOf(
        AddressRange(IPV4_BYTES, listOf(10), 8),
        AddressRange(IPV4_BYTES, listOf(172, 16), 12),
        AddressRange(IPV4_BYTES, listOf(192, 168), 16),
        AddressRange(IPV4_BYTES, listOf(169, 254), 16),
        AddressRange(IPV4_BYTES, listOf(100, 64), 10),
        AddressRange(IPV4_BYTES, listOf(224), 4),
        AddressRange(IPV4_BYTES, listOf(255, 255, 255, 255), 32),
        AddressRange(IPV6_BYTES, listOf(0xFE, 0x80), 10),
        AddressRange(IPV6_BYTES, listOf(0xFF), 8),
        AddressRange(IPV6_BYTES, listOf(0xFC), 7),
    )

private class AddressRange(
    private val size: Int,
    private val prefix: List<Int>,
    private val bits: Int,
) {
    fun contains(address: ByteArray): Boolean =
        address.size == size &&
            (0 until bits).all { bit ->
                val index = bit / BITS_PER_BYTE
                val mask = HIGH_BIT ushr (bit % BITS_PER_BYTE)
                (address[index].toInt() and mask) == (prefix.getOrElse(index) { 0 } and mask)
            }
}
