package app.paralleluniverse.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/** PC가 접속할 휴대폰의 로컬 IPv4 주소를 찾는다. 찾지 못하면 null. */
object NetworkAddress {
    fun find(context: Context): String? {
        val candidates = mutableListOf<AddressCandidate>()
        candidates += fromConnectivity(context)
        candidates += fromInterfaces()
        return AddressPicker.pick(candidates)
    }

    /** Wi-Fi·이더넷 네트워크의 주소(ACCESS_NETWORK_STATE 필요). VPN 네트워크는 건너뛴다. */
    private fun fromConnectivity(context: Context): List<AddressCandidate> = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val result = mutableListOf<AddressCandidate>()
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val source = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> AddressCandidate.Source.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> AddressCandidate.Source.ETHERNET
                else -> continue
            }
            val link = cm.getLinkProperties(network) ?: continue
            for (linkAddress in link.linkAddresses) {
                val address = linkAddress.address
                if (address is Inet4Address) {
                    val text = address.hostAddress ?: continue
                    result += AddressCandidate(link.interfaceName.orEmpty(), text, source)
                }
            }
        }
        result
    }.getOrDefault(emptyList())

    /** 휴대폰 핫스팟·USB 테더링처럼 ConnectivityManager에 나오지 않는 인터페이스의 주소. */
    private fun fromInterfaces(): List<AddressCandidate> = runCatching {
        val result = mutableListOf<AddressCandidate>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (ni in interfaces) {
            if (!ni.isUp || ni.isLoopback) continue
            for (address in ni.inetAddresses) {
                if (address is Inet4Address) {
                    val text = address.hostAddress ?: continue
                    result += AddressCandidate(ni.name, text, AddressCandidate.Source.INTERFACE)
                }
            }
        }
        result
    }.getOrDefault(emptyList())
}
