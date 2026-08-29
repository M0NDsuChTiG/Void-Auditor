package com.kuzyamond.voidauditor.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class EvidenceParserTest {

    private val defaultRouteParser = DefaultRouteParser()
    private val wifiInfoParser = WifiInfoParser()
    private val packageDetailsParser = PackageDetailsParser()

    // --- DefaultRouteParser tests ---

    @ParameterizedTest
    @MethodSource("defaultRouteCases")
    fun testDefaultRouteParser(capability: Capability.ReadDefaultRoute, result: ShizukuExecutor.CommandResult, expectedInterface: String?, expectedGateway: String?) {
        val evidence = defaultRouteParser.parse(capability, result) as? DefaultRouteEvidence
        if (expectedInterface == null && expectedGateway == null) {
            assert(evidence == null) { "Expected null for failed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedInterface, evidence.interfaceName)
            assertEquals(expectedGateway, evidence.gateway)
        }
    }

    companion object {
        @JvmStatic
        fun defaultRouteCases(): Stream<Arguments> = Stream.of(
            // Valid route with interface and gateway
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 dev wlan0 proto dhcp", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", "192.168.1.1"
            ),
            // Valid route with different interface
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "default via 10.0.0.1 dev eth0 metric 100", error = "", exitCode = 0, executionTimeMs = 10),
                "eth0", "10.0.0.1"
            ),
            // Missing gateway
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "default dev wlan0 proto static", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", null
            ),
            // Missing interface
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 metric 50", error = "", exitCode = 0, executionTimeMs = 10),
                null, "192.168.1.1"
            ),
            // Empty output
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Command failed
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null, null
            ),
            // Malformed output
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "not a route line", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Multiple routes - first one used
            Arguments.of(
                Capability.ReadDefaultRoute(),
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 dev wlan0\n192.168.2.0/24 dev eth0", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", "192.168.1.1"
            )
        )
    }

    // --- WifiInfoParser tests ---

    @ParameterizedTest
    @MethodSource("wifiInfoCases")
    fun testWifiInfoParser(capability: Capability.ReadWifiInfo, result: ShizukuExecutor.CommandResult, expectedSsid: String?, expectedBssid: String?) {
        val evidence = wifiInfoParser.parse(capability, result) as? WifiEvidence
        if (expectedSsid == null && expectedBssid == null) {
            assert(evidence == null) { "Expected null for failed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedSsid, evidence.ssid)
            assertEquals(expectedBssid, evidence.bssid)
        }
    }

    companion object {
        @JvmStatic
        fun wifiInfoCases(): Stream<Arguments> = Stream.of(
            // Both SSID and BSSID present
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "SSID: MyWiFi\nBSSID: aa:bb:cc:dd:ee:ff\nRSSI: -45", error = "", exitCode = 0, executionTimeMs = 10),
                "MyWiFi", "aa:bb:cc:dd:ee:ff"
            ),
            // SSID only
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "SSID: GuestNetwork\nRSSI: -60", error = "", exitCode = 0, executionTimeMs = 10),
                "GuestNetwork", null
            ),
            // BSSID only
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "BSSID: 11:22:33:44:55:66\nRSSI: -50", error = "", exitCode = 0, executionTimeMs = 10),
                null, "11:22:33:44:55:66"
            ),
            // Empty output
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Command failed
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null, null
            ),
            // Malformed output
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "unknown format", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Extra whitespace
            Arguments.of(
                Capability.ReadWifiInfo(),
                ShizukuExecutor.CommandResult(success = true, output = "  SSID:  My Network  \n  BSSID:  aa:bb:cc:dd:ee:ff  ", error = "", exitCode = 0, executionTimeMs = 10),
                "My Network", "aa:bb:cc:dd:ee:ff"
            )
        )
    }

    // --- PackageDetailsParser tests ---

    @ParameterizedTest
    @MethodSource("packageDetailsCases")
    fun testPackageDetailsParser(capability: Capability.ReadPackageDetails, result: ShizukuExecutor.CommandResult,
                                 expectedVersionName: String?, expectedVersionCode: Long?, expectedInstaller: String?, expectedPermissions: List<String>) {
        val evidence = packageDetailsParser.parse(capability, result) as? PackageDetailsEvidence
        if (expectedVersionName == null && expectedVersionCode == null && expectedInstaller == null && expectedPermissions.isEmpty()) {
            assert(evidence == null) { "Expected null for failed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(capability.packageName, evidence.packageName)
            assertEquals(expectedVersionName, evidence.versionName)
            assertEquals(expectedVersionCode, evidence.versionCode)
            assertEquals(expectedInstaller, evidence.installerPackageName)
            assertEquals(expectedPermissions.toSet(), evidence.permissions.toSet())
        }
    }

    companion object {
        @JvmStatic
        fun packageDetailsCases(): Stream<Arguments> = Stream.of(
            // Complete package details
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=123
                    versionName=1.2.3
                    installerPackageName=com.android.vending
                    android.permission.INTERNET: granted=true
                    android.permission.ACCESS_FINE_LOCATION: granted=true
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.2.3", 123L, "com.android.vending",
                listOf("android.permission.INTERNET", "android.permission.ACCESS_FINE_LOCATION")
            ),
            // Missing versionName
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=456
                    installerPackageName=com.google.android.feedback
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                null, 456L, "com.google.android.feedback",
                emptyList()
            ),
            // Missing installer
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=789
                    versionName=2.0.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "2.0.0", 789L, null,
                emptyList()
            ),
            // Multiple permissions
            Arguments.of(
                Capability.ReadPackageDetails("com.bank.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.bank.app
                    versionCode=100
                    versionName=1.0.0
                    android.permission.SEND_SMS: granted=true
                    android.permission.READ_SMS: granted=true
                    android.permission.READ_CONTACTS: granted=true
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0.0", 100L, null,
                listOf("android.permission.SEND_SMS", "android.permission.READ_SMS", "android.permission.READ_CONTACTS")
            ),
            // No permissions
            Arguments.of(
                Capability.ReadPackageDetails("com.simple.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.simple.app
                    versionCode=1
                    versionName=1.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0", 1L, null,
                emptyList()
            ),
            // Malformed versionCode
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=not_a_number
                    versionName=1.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0", null, null,
                emptyList()
            ),
            // Command failed
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PACKAGE_NOT_FOUND", exitCode = -1, executionTimeMs = 10),
                null, null, null,
                emptyList()
            ),
            // Empty output
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null, null, null,
                emptyList()
            ),
            // Command failed with error output
            Arguments.of(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = false, output = "error", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null, null, null,
                emptyList()
            )
        )
    }
}