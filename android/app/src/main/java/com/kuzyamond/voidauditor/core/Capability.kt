package com.kuzyamond.voidauditor.core

sealed class Capability(override val description: String, override val riskScore: Int) : USFPipeline.Capability {
    data class ReadSystemProp(val prop: String = "*") : Capability("Read system property: $prop", 10)
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
    data class ExecuteArbitraryShell(val commandString: String) : Capability("Shell: $commandString", 85)
    data class ConfigureAdbTcp(val port: Int) : Capability("Configure ADB TCP on port $port", 75)
    data class DumpPackageActivities(val packageName: String) : Capability("Dump activities: $packageName", 25)
    data class LaunchActivity(val component: String) : Capability("Launch: $component", 45)
}
