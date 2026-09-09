/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.manager.bookmarks

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/**
 * Whether an address is one this app should go and fetch.
 *
 * The usual reason to block private ranges is a server being tricked into
 * reaching its own network. Here it is the other way round and worse: a phone
 * would be reaching *the reader's own* network. A bookmark pointing at
 * `192.168.1.1` — or a redirect that lands there — would have Whisper probing
 * the household router, the printer and the NAS for `/feed.xml`, from inside
 * the firewall, using the reader's own device to do it.
 *
 * Checked after every redirect rather than only at the start, because a
 * public host that redirects to a private one is the whole trick.
 */
object SafeAddress {

    /** Scheme check only, for an address that has not been resolved yet. */
    fun schemeAllowed(url: String): Boolean {
        val scheme = runCatching { URL(url).protocol?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    /**
     * Whether a resolved address is outside the reader's own network.
     *
     * Loopback, link-local, site-local and the multicast and wildcard ranges
     * are all refused. `isSiteLocalAddress` covers 10/8, 172.16/12 and
     * 192.168/16; the rest are named because it does not.
     */
    fun isPublic(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isLinkLocalAddress) return false
        if (address.isSiteLocalAddress) return false
        if (address.isAnyLocalAddress) return false
        if (address.isMulticastAddress) return false

        return when (address) {
            // 100.64/10, carrier-grade NAT — not the reader's LAN, but not
            // somewhere to be probing either.
            is Inet4Address -> {
                val b = address.address
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                !(first == 100 && second in 64..127) && first != 0
            }
            // fc00::/7, unique local addresses: IPv6's private range, which
            // isSiteLocalAddress does not report.
            is Inet6Address -> (address.address[0].toInt() and 0xFE) != 0xFC
            else -> true
        }
    }

    /** The same question for a hostname, resolving it first. */
    fun isPublicHost(host: String): Boolean =
        runCatching { InetAddress.getAllByName(host).all(::isPublic) }.getOrDefault(false)
}

/**
 * Refuses any hop that lands on the reader's own network.
 *
 * A **network** interceptor rather than an application one, and the difference
 * is the whole point: OkHttp follows redirects itself, and an application
 * interceptor runs once for the call while a network interceptor runs for
 * every hop. Checking only the address that was typed would miss a public host
 * that redirects to `192.168.1.1`, which is exactly the trick worth stopping —
 * a bookmark cannot be trusted to stay where it points.
 */
class BlockPrivateNetworks : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val host = chain.request().url.host
        if (!SafeAddress.isPublicHost(host)) {
            throw java.io.IOException("Refusing to fetch a private address: $host")
        }
        return chain.proceed(chain.request())
    }
}
