package com.kyro.game.space.data.model

enum class NetworkTransportType(val label: String) {
    WIFI("Wi-Fi Network"),
    MOBILE_DATA("Mobile Data (4G/5G)"),
    OTHER("Active Network"),
    OFFLINE("Offline")
}

data class DnsPreset(
    val id: String,
    val name: String,
    val badge: String,
    val primaryIp: String,
    val secondaryIp: String,
    val privateDnsHostname: String,
    val description: String
)

data class DnsBenchmarkResult(
    val preset: DnsPreset,
    val avgPingMs: Int? = null,
    val minPingMs: Int? = null,
    val maxPingMs: Int? = null,
    val jitterMs: Int? = null,
    val sampleCount: Int = 0,
    val stabilityPercent: Int = 0,
    val isReachable: Boolean = false
)

data class NetworkOptimizerState(
    val activeTransport: NetworkTransportType = NetworkTransportType.WIFI,
    val transportDetails: String = "Checking active interface...",
    val selectedDnsId: String = "cloudflare",
    val activeDnsName: String = "Cloudflare Gaming",
    val activePingHost: String = "1.1.1.1",
    val privateDnsHostname: String = "one.one.one.one",
    val wifiLowLatencyEnabled: Boolean = false,
    val mobileDataBoostEnabled: Boolean = false,
    val backgroundScanDisabled: Boolean = false,
    val isBenchmarking: Boolean = false,
    val benchmarkProgress: Float = 0f,
    val currentRound: Int = 0,
    val totalRounds: Int = 6,
    val isRepairing: Boolean = false,
    val benchmarkResults: List<DnsBenchmarkResult> = DEFAULT_DNS_PRESETS.map { DnsBenchmarkResult(it) },
    val statusLog: String = "Tap 'Select Stable Network' to sample all DNS servers over several seconds."
) {
    companion object {
        val DEFAULT_DNS_PRESETS = listOf(
            DnsPreset(
                id = "cloudflare",
                name = "Cloudflare Gaming",
                badge = "Ultra Low Ping",
                primaryIp = "1.1.1.1",
                secondaryIp = "1.0.0.1",
                privateDnsHostname = "one.one.one.one",
                description = "Fastest global edge routing for competitive multiplayer games."
            ),
            DnsPreset(
                id = "google",
                name = "Google Public DNS",
                badge = "High Stability",
                primaryIp = "8.8.8.8",
                secondaryIp = "8.8.4.4",
                privateDnsHostname = "dns.google",
                description = "Reliable worldwide backbone with consistent packet delivery."
            ),
            DnsPreset(
                id = "quad9",
                name = "Quad9 Anycast",
                badge = "Anti-Jitter",
                primaryIp = "9.9.9.9",
                secondaryIp = "149.112.112.112",
                privateDnsHostname = "dns.quad9.net",
                description = "High-availability routing with low packet loss on cellular & Wi-Fi."
            ),
            DnsPreset(
                id = "opendns",
                name = "Cisco OpenDNS",
                badge = "ISP Bypass",
                primaryIp = "208.67.222.222",
                secondaryIp = "208.67.220.220",
                privateDnsHostname = "doh.opendns.com",
                description = "Direct peering routes to avoid congested local ISP resolvers."
            ),
            DnsPreset(
                id = "adguard",
                name = "AdGuard Gaming",
                badge = "Zero Telemetry",
                primaryIp = "94.140.14.14",
                secondaryIp = "94.140.15.15",
                privateDnsHostname = "dns.adguard-dns.com",
                description = "Blocks background ad/tracker traffic that causes in-game ping spikes."
            ),
            DnsPreset(
                id = "controld",
                name = "ControlD Unfiltered",
                badge = "Direct Route",
                primaryIp = "76.76.2.0",
                secondaryIp = "76.76.10.0",
                privateDnsHostname = "freedns.controld.com",
                description = "Modern anycast DNS engineered for low-latency gaming sessions."
            )
        )
    }
}
