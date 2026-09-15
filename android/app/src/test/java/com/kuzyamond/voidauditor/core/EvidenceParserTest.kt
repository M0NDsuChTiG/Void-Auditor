package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.core.evidence.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.*

class EvidenceParserTest {

    private val defaultRouteParser = DefaultRouteParser()
    private val wifiInfoParser = WifiInfoParser()
    private val packageDetailsParser = PackageDetailsParser()
    private val systemPropParser = SystemPropParser()
    private val appOpsParser = AppOpsParser()
    private val serviceStateParser = ServiceStateParser()

    // --- DefaultRouteParser tests ---

    @ParameterizedTest
    @MethodSource("defaultRouteCases")
    fun testDefaultRouteParser(capability: Capability.ReadDefaultRoute, result: ShizukuExecutor.CommandResult, expectedInterface: String?, expectedGateway: String?) {
        val evidence = defaultRouteParser.parse(capability, result) as? DefaultRouteEvidence
        if (expectedInterface == null && expectedGateway == null) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedInterface, evidence?.interfaceName)
            assertEquals(expectedGateway, evidence?.gateway)
        }
    }

    // --- WifiInfoParser tests ---

    @ParameterizedTest
    @MethodSource("wifiInfoCases")
    fun testWifiInfoParser(capability: Capability.ReadWifiInfo, result: ShizukuExecutor.CommandResult, expectedSsid: String?, expectedBssid: String?) {
        val evidence = wifiInfoParser.parse(capability, result) as? WifiEvidence
        if (expectedSsid == null && expectedBssid == null) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedSsid, evidence?.ssid)
            assertEquals(expectedBssid, evidence?.bssid)
        }
    }

    // --- PackageDetailsParser tests ---

    @ParameterizedTest
    @MethodSource("packageDetailsCases")
    fun testPackageDetailsParser(capability: Capability.ReadPackageDetails, result: ShizukuExecutor.CommandResult,
                                 expectedVersionName: String?, expectedVersionCode: Long?, expectedInstaller: String?, expectedPermissions: List<String>) {
        val evidence = packageDetailsParser.parse(capability, result) as? PackageDetailsEvidence
        if (expectedVersionName == null && expectedVersionCode == null && expectedInstaller == null && expectedPermissions.isEmpty()) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(capability.packageName, evidence?.packageName)
            assertEquals(expectedVersionName, evidence?.versionName)
            assertEquals(expectedVersionCode, evidence?.versionCode)
            assertEquals(expectedInstaller, evidence?.installerPackageName)
            assertEquals(expectedPermissions.toSet(), evidence?.permissions?.toSet())
        }
    }

    // --- SystemPropParser tests ---

    @ParameterizedTest
    @MethodSource("systemPropCases")
    fun testSystemPropParser(capability: Capability.ReadSystemProp, result: ShizukuExecutor.CommandResult, expectedProp: String?, expectedValue: String?) {
        val evidence = systemPropParser.parse(capability, result) as? SystemPropEvidence
        if (expectedValue == null) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedProp, evidence?.prop)
            assertEquals(expectedValue, evidence?.value)
        }
    }

    // --- AppOpsParser tests ---

    @ParameterizedTest
    @MethodSource("appOpsCases")
    fun testAppOpsParser(capability: Capability.ReadAppOps, result: ShizukuExecutor.CommandResult, expectedOp: String?, expectedOutput: String?) {
        val evidence = appOpsParser.parse(capability, result) as? AppOpsEvidence
        if (expectedOutput == null) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedOp, evidence?.op)
            assertEquals(expectedOutput, evidence?.output)
        }
    }

    // --- ServiceStateParser tests ---

    @ParameterizedTest
    @MethodSource("serviceStateCases")
    fun testServiceStateParser(capability: Capability.ReadServiceState, result: ShizukuExecutor.CommandResult, expectedService: String?, expectedOutput: String?) {
        val evidence = serviceStateParser.parse(capability, result) as? ServiceStateEvidence
        if (expectedOutput == null) {
            assertNull(evidence) { "Expected null for failed/malformed command" }
        } else {
            assertNotNull(evidence) { "Expected parsed evidence" }
            assertEquals(expectedService, evidence?.service)
            assertEquals(expectedOutput, evidence?.output)
        }
    }

    companion object {
        private fun args(vararg args: Any?): Arguments = Arguments.of(*args)

        @JvmStatic
        fun defaultRouteCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Valid route with interface and gateway
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 dev wlan0 proto dhcp", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", "192.168.1.1"
            ),
            // Valid route with different interface
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "default via 10.0.0.1 dev eth0 metric 100", error = "", exitCode = 0, executionTimeMs = 10),
                "eth0", "10.0.0.1"
            ),
            // Missing gateway
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "default dev wlan0 proto static", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", null
            ),
            // Missing interface
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 metric 50", error = "", exitCode = 0, executionTimeMs = 10),
                null, "192.168.1.1"
            ),
            // Empty output (success but no default route)
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Command failed
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null, null
            ),
            // Malformed output
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "not a route line", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Multiple routes - first default used
            args(
                Capability.ReadDefaultRoute,
                ShizukuExecutor.CommandResult(success = true, output = "default via 192.168.1.1 dev wlan0\n192.168.2.0/24 dev eth0", error = "", exitCode = 0, executionTimeMs = 10),
                "wlan0", "192.168.1.1"
            )
        )

        @JvmStatic
        fun wifiInfoCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Both SSID and BSSID present
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "SSID: MyWiFi\nBSSID: aa:bb:cc:dd:ee:ff\nRSSI: -45", error = "", exitCode = 0, executionTimeMs = 10),
                "MyWiFi", "aa:bb:cc:dd:ee:ff"
            ),
            // SSID only
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "SSID: GuestNetwork\nRSSI: -60", error = "", exitCode = 0, executionTimeMs = 10),
                "GuestNetwork", null
            ),
            // BSSID only
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "BSSID: 11:22:33:44:55:66\nRSSI: -50", error = "", exitCode = 0, executionTimeMs = 10),
                null, "11:22:33:44:55:66"
            ),
            // Empty output (success but no wifi info)
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Command failed
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null, null
            ),
            // Malformed output
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "unknown format", error = "", exitCode = 0, executionTimeMs = 10),
                null, null
            ),
            // Extra whitespace
            args(
                Capability.ReadWifiInfo,
                ShizukuExecutor.CommandResult(success = true, output = "  SSID:  My Network  \n  BSSID:  aa:bb:cc:dd:ee:ff  ", error = "", exitCode = 0, executionTimeMs = 10),
                "My Network", "aa:bb:cc:dd:ee:ff"
            )
        )

        @JvmStatic
        fun packageDetailsCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Complete package details
            args(
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
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=456
                    installerPackageName=com.google.android.feedback
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                null as String?, 456L, "com.google.android.feedback",
                emptyList<String>()
            ),
            // Missing installer
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=789
                    versionName=2.0.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "2.0.0", 789L, null as String?,
                emptyList<String>()
            ),
            // Multiple permissions
            args(
                Capability.ReadPackageDetails("com.bank.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.bank.app
                    versionCode=100
                    versionName=1.0.0
                    android.permission.SEND_SMS: granted=true
                    android.permission.READ_SMS: granted=true
                    android.permission.READ_CONTACTS: granted=true
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0.0", 100L, null as String?,
                listOf("android.permission.SEND_SMS", "android.permission.READ_SMS", "android.permission.READ_CONTACTS")
            ),
            // No permissions
            args(
                Capability.ReadPackageDetails("com.simple.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.simple.app
                    versionCode=1
                    versionName=1.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0", 1L, null as String?,
                emptyList<String>()
            ),
            // Malformed versionCode
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    package com.example.app
                    versionCode=not_a_number
                    versionName=1.0
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "1.0", null as Long?, null as String?,
                emptyList<String>()
            ),
            // Command failed
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PACKAGE_NOT_FOUND", exitCode = -1, executionTimeMs = 10),
                null as String?, null as Long?, null as String?,
                emptyList<String>()
            ),
            // Empty output (success but no package info)
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null as String?, null as Long?, null as String?,
                emptyList<String>()
            ),
            // Command failed with error output
            args(
                Capability.ReadPackageDetails("com.example.app"),
                ShizukuExecutor.CommandResult(success = false, output = "error", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null as String?, null as Long?, null as String?,
                emptyList<String>()
            )
        )

        // --- SystemPropParser cases ---

        @JvmStatic
        fun systemPropCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Valid property with value
            args(
                Capability.ReadSystemProp("ro.build.type"),
                ShizukuExecutor.CommandResult(success = true, output = "user", error = "", exitCode = 0, executionTimeMs = 10),
                "ro.build.type", "user"
            ),
            // Different property
            args(
                Capability.ReadSystemProp("ro.debuggable"),
                ShizukuExecutor.CommandResult(success = true, output = "0", error = "", exitCode = 0, executionTimeMs = 10),
                "ro.debuggable", "0"
            ),
            // Empty output (property not set)
            args(
                Capability.ReadSystemProp("ro.unknown.prop"),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null as String?, null as String?
            ),
            // Command failed
            args(
                Capability.ReadSystemProp("ro.build.type"),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null as String?, null as String?
            )
        )

        // --- AppOpsParser cases ---

        @JvmStatic
        fun appOpsCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Allowed operation
            args(
                Capability.ReadAppOps("BLUETOOTH_SCAN"),
                ShizukuExecutor.CommandResult(success = true, output = "allow", error = "", exitCode = 0, executionTimeMs = 10),
                "BLUETOOTH_SCAN", "allow"
            ),
            // Denied operation
            args(
                Capability.ReadAppOps("SMS"),
                ShizukuExecutor.CommandResult(success = true, output = "deny", error = "", exitCode = 0, executionTimeMs = 10),
                "SMS", "deny"
            ),
            // Empty output (success but no result)
            args(
                Capability.ReadAppOps("CAMERA"),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null as String?, null as String?
            ),
            // Command failed
            args(
                Capability.ReadAppOps("BLUETOOTH_SCAN"),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "PERMISSION_DENIED", exitCode = -1, executionTimeMs = 10),
                null as String?, null as String?
            )
        )

        // --- ServiceStateParser cases ---

        @JvmStatic
        fun serviceStateCases(): java.util.stream.Stream<Arguments> = java.util.stream.Stream.of(
            // Valid service dump
            args(
                Capability.ReadServiceState("bluetooth_manager"),
                ShizukuExecutor.CommandResult(success = true, output = """
                    Service bluetooth_manager:
                      Client 0: "com.android.bluetooth" r0
                      Client 1: "com.android.settings" r1
                """.trimIndent(), error = "", exitCode = 0, executionTimeMs = 50),
                "bluetooth_manager",
                "Service bluetooth_manager:\n  Client 0: \"com.android.bluetooth\" r0\n  Client 1: \"com.android.settings\" r1"
            ),
            // Empty output
            args(
                Capability.ReadServiceState("unknown_service"),
                ShizukuExecutor.CommandResult(success = true, output = "", error = "", exitCode = 0, executionTimeMs = 10),
                null as String?, null as String?
            ),
            // Command failed
            args(
                Capability.ReadServiceState("bluetooth_manager"),
                ShizukuExecutor.CommandResult(success = false, output = "", error = "SERVICE_NOT_FOUND", exitCode = -1, executionTimeMs = 10),
                null as String?, null as String?
            )
        )
    }
}