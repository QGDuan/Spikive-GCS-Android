package com.example.spikivegcs.network

import java.net.Inet6Address
import java.net.InetAddress

internal fun InetAddress.isUsableForGroundStation(): Boolean =
    !isAnyLocalAddress &&
        !isLoopbackAddress &&
        !isMulticastAddress &&
        !(this is Inet6Address && isLinkLocalAddress)
