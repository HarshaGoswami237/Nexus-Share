package com.example.localfileshare.network

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Retrieves the active IPv4 address for Wi-Fi or Hotspot interfaces.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // Filter out inactive or loopback interfaces
                if (!intf.isUp || intf.isLoopback) continue

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0

                        if (isIPv4) {
                            // Exclude Android loopback placeholders if present
                            if (sAddr != "127.0.0.1") {
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}