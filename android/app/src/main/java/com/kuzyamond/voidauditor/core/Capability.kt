package com.kuzyamond.voidauditor.core

sealed class Capability(override val description: String, override val riskScore: Int) : USFPipeline.Capability {
    // READ tier (risk 5-20)
    data class ReadSystemProp(val prop: String = "*") : Capability("Read system property: $prop", 10)
    data object ReadSystemFeatures : Capability("Read system features", 10)
    data object ReadUserIdentity : Capability("Read user identity", 10)
    data class ReadPackageDetails(val packageName: String) : Capability("Read package details: $packageName", 15)
    data object ReadPackageCount : Capability("Read third-party package count", 10)
    data object ReadDangerousPermissions : Capability("Read dangerous permissions", 15)
    data class ReadDiskUsage(val path: String) : Capability("Read disk usage: $path", 10)
    data class ReadDirectorySize(val path: String) : Capability("Read directory size: $path", 10)
    data class ReadFileCount(val path: String) : Capability("Read file count: $path", 10)
    data class ReadLastModified(val path: String) : Capability("Read last modified: $path", 10)
    data object ReadARPTable : Capability("Read ARP table", 10)
    data class ReadAppOps(val op: String) : Capability("Read appops: $op", 15)
    data class ReadSetting(val namespace: String, val key: String) : Capability("Read setting: $namespace/$key", 15)
    data object ReadDefaultRoute : Capability("Read default network route", 10)
    data object ReadWifiInfo : Capability("Read Wi-Fi connection information", 15)
    data class ReadServiceState(val service: String) : Capability("Read service state: $service", 15)
    data class DiscoverCacheDirectories(val roots: List<String>, val maxDepth: Int) : Capability("Discover cache directories", 15)

    // ACTION tier (risk 25-60)
    data class ExecuteSystemTrim(val freeBytesHint: String) : Capability("Execute system trim: $freeBytesHint", 50)

    sealed class CacheCapability(override val description: String) : Capability(description, 30) {
        data object AppCache : CacheCapability("App cache dry run")
        data object SystemCache : CacheCapability("System cache dry run")
        data object TempFiles : CacheCapability("Temp files dry run")
        data object UserCache : CacheCapability("User cache dry run")
    }

    data class ExecuteDryRun(val capability: CacheCapability) : Capability("Execute dry run: ${capability.description}", 30)
    data class ExecuteClean(val capability: CacheCapability) : Capability("Execute clean: ${capability.description}", 60)
    data class ExecuteNetworkScript(val script: String) : Capability("Execute network script", 60)

    // REMEDIATION tier (risk 70+)
    sealed class RemediationIntent(override val description: String, override val riskScore: Int) : USFPipeline.Capability {
        data object EnableFirewall : RemediationIntent("Enable system firewall", 80)
        data object DisableDebuggable : RemediationIntent("Disable debuggable flag", 85)
        data object HardenSsh : RemediationIntent("Harden SSH configuration", 75)
        data object DisableService : RemediationIntent("Disable vulnerable service", 70)
    }

    // ARBITRARY tier (risk 85+)
    data class ExecuteArbitraryShell(val commandString: String) : Capability("Shell: $commandString", 85)
    enum class ScriptLanguage { BASH, PYTHON3 }
    data class ExecuteScript(val language: ScriptLanguage, val payload: String) : Capability("Execute $language script", 85)

    // Existing capabilities (kept for compatibility / specific use cases)
    data class RunShellCommand(val commandHint: String) : Capability("Shell: $commandHint", 30)
    data class QueryPackages(val filter: String = "all") : Capability("Query packages: $filter", 15)
    data class DumpService(val service: String) : Capability("Dumpsys: $service", 20)
    data class ModifySettings(val namespace: String, val key: String) : Capability("Modify setting: $namespace/$key", 70)
    data class InstallPackage(val packageName: String) : Capability("Install: $packageName", 90)
    data class UninstallPackage(val packageName: String) : Capability("Uninstall: $packageName", 85)
    data class ForceStopPackage(val packageName: String) : Capability("Force stop: $packageName", 50)
    data class ClearAppData(val packageName: String) : Capability("Clear data: $packageName", 75)
    data class ReadFile(val path: String) : Capability("Read file: $path", 40)
    data class WriteFile(val path: String) : Capability("Write file: $path", 80)
    data class RunAsRoot(val commandHint: String) : Capability("Root: $commandHint", 95)
    data class NetworkAction(val action: String) : Capability("Network: $action", 60)
    data class ReadSensitiveData(val dataType: String) : Capability("Read $dataType", 65)
    data class CleanCache(val path: String, val safeCommand: String) : Capability("Clean cache: $path", 30)
    data class ConfigureAdbTcp(val port: Int) : Capability("Configure ADB TCP on port $port", 75)
    data class DumpPackageActivities(val packageName: String) : Capability("Dump activities: $packageName", 25)
    data class LaunchActivity(val component: String) : Capability("Launch: $component", 45)
    data class ListDirectory(val path: String) : Capability("List directory: $path", 15)
    data class CalculateDiskUsage(val path: String) : Capability("Disk usage: $path", 10)
    data class DisablePackage(val packageName: String) : Capability("Disable package: $packageName", 60)
    data class EnablePackage(val packageName: String) : Capability("Enable package: $packageName", 30)
    data class AdbConnect(val ipAddress: String, val port: Int) : Capability("ADB connect: $ipAddress:$port", 50)
    data object AdbScanDevices : Capability("Scan ADB devices", 10)
    data class ListApkFiles(val path: String) : Capability("List APK files: $path", 15)
    data class InstallApk(val filePath: String) : Capability("Install APK: $filePath", 85)
    data class GetPackagePath(val packageName: String) : Capability("Get package path: $packageName", 15)
    data class CopyFile(val source: String, val destination: String) : Capability("Copy file: $source → $destination", 50)
    data class CreateDirectory(val path: String) : Capability("Create directory: $path", 30)
}