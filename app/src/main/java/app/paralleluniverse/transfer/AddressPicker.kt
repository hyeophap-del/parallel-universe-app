package app.paralleluniverse.transfer

/** 휴대폰의 IPv4 주소 후보 하나. */
data class AddressCandidate(
    val interfaceName: String,
    val address: String,
    val source: Source,
) {
    /** 앞에 있을수록 우선한다. */
    enum class Source { WIFI, ETHERNET, INTERFACE }
}

/**
 * 같은 네트워크의 PC가 접속할 수 있는 주소를 고른다.
 *
 * ConnectivityManager가 알려 준 Wi-Fi/이더넷 주소를 먼저 쓰고, 없으면 인터페이스 이름으로
 * 핫스팟(ap, swlan)·USB 테더링(rndis) 주소를 찾는다. 모바일 데이터(rmnet 등)와 VPN(tun)은 쓰지 않는다.
 */
object AddressPicker {
    private val LOCAL_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "eth", "rndis", "bt-pan")

    fun pick(candidates: List<AddressCandidate>): String? =
        candidates
            .filter { isUsableIpv4(it.address) }
            .filter { it.source != AddressCandidate.Source.INTERFACE || isLocalInterface(it.interfaceName) }
            .minByOrNull { it.source.ordinal * 2 + if (isPrivate(it.address)) 0 else 1 }
            ?.address

    fun isLocalInterface(name: String): Boolean = LOCAL_PREFIXES.any { name.startsWith(it) }

    fun isUsableIpv4(address: String): Boolean {
        val o = octets(address) ?: return false
        return when {
            o[0] == 0 || o[0] == 127 -> false
            o[0] == 169 && o[1] == 254 -> false
            o[0] >= 224 -> false
            else -> true
        }
    }

    fun isPrivate(address: String): Boolean {
        val o = octets(address) ?: return false
        return o[0] == 10 ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 192 && o[1] == 168)
    }

    private fun octets(address: String): List<Int>? {
        val parts = address.split('.')
        if (parts.size != 4) return null
        val values = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            part.toInt()
        }
        return values.takeIf { v -> v.all { it in 0..255 } }
    }
}
