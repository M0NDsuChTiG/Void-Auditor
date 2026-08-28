package com.kuzyamond.voidauditor.core

object CapabilityValidator {

    private val ANDROID_PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val SAFE_PATH_REGEX = Regex("^[/a-zA-Z0-9_.-]+$")
    private val SETTINGS_NAMESPACE_REGEX = Regex("^(global|secure|system)$")
    private val SETTINGS_KEY_REGEX = Regex("^[a-z_][a-z0-9_]*$")
    private val APPOPS_OP_REGEX = Regex("^[A-Z_][A-Z0-9_]*$")
    private val SERVICE_NAME_REGEX = Regex("^[a-z_][a-z0-9_]*$")
    private val FREE_BYTES_HINT_REGEX = Regex("^\\d+[KMG]$")
    private val CACHE_ROOTS_REGEX = Regex("^(/data|/sdcard|/storage)(/.*)?$")

    sealed class ValidationResult {
        data class Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    fun validate(capability: Capability): ValidationResult {
        val errors = mutableListOf<String>()

        when (capability) {
            is Capability.ReadPackageDetails -> validatePackageName(capability.packageName, errors)
            is Capability.ReadAppOps -> validateAppOpsOp(capability.op, errors)
            is Capability.ReadSetting -> {
                validateSettingsNamespace(capability.namespace, errors)
                validateSettingsKey(capability.key, errors)
            }
            is Capability.ReadServiceState -> validateServiceName(capability.service, errors)
            is Capability.ReadDiskUsage -> validateSafePath(capability.path, errors)
            is Capability.ReadDirectorySize -> validateSafePath(capability.path, errors)
            is Capability.ReadFileCount -> validateSafePath(capability.path, errors)
            is Capability.ReadLastModified -> validateSafePath(capability.path, errors)
            is Capability.DiscoverCacheDirectories -> validateCacheRoots(capability.roots, capability.maxDepth, errors)
            is Capability.ExecuteSystemTrim -> validateFreeBytesHint(capability.freeBytesHint, errors)
            else -> {}
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    private fun validatePackageName(packageName: String, errors: MutableList<String>) {
        if (packageName.isBlank()) {
            errors.add("packageName: empty")
        } else if (!ANDROID_PACKAGE_REGEX.matches(packageName)) {
            errors.add("packageName: invalid format (expected: com.example.app)")
        }
    }

    private fun validateAppOpsOp(op: String, errors: MutableList<String>) {
        if (op.isBlank()) {
            errors.add("op: empty")
        } else if (!APPOPS_OP_REGEX.matches(op)) {
            errors.add("op: invalid format (expected: PERMISSION_CONSTANT)")
        }
    }

    private fun validateSettingsNamespace(namespace: String, errors: MutableList<String>) {
        if (namespace.isBlank()) {
            errors.add("namespace: empty")
        } else if (!SETTINGS_NAMESPACE_REGEX.matches(namespace)) {
            errors.add("namespace: must be global|secure|system")
        }
    }

    private fun validateSettingsKey(key: String, errors: MutableList<String>) {
        if (key.isBlank()) {
            errors.add("key: empty")
        } else if (!SETTINGS_KEY_REGEX.matches(key)) {
            errors.add("key: invalid format (expected: snake_case)")
        }
    }

    private fun validateServiceName(service: String, errors: MutableList<String>) {
        if (service.isBlank()) {
            errors.add("service: empty")
        } else if (!SERVICE_NAME_REGEX.matches(service)) {
            errors.add("service: invalid format (expected: snake_case)")
        }
    }

    private fun validateSafePath(path: String, errors: MutableList<String>) {
        if (path.isBlank()) {
            errors.add("path: empty")
        } else if (!SAFE_PATH_REGEX.matches(path)) {
            errors.add("path: contains invalid characters (allowed: / a-z A-Z 0-9 _ . -)")
        } else if (path.contains("..")) {
            errors.add("path: directory traversal not allowed")
        } else if (!path.startsWith("/")) {
            errors.add("path: must be absolute")
        }
    }

    private fun validateCacheRoots(roots: List<String>, maxDepth: Int, errors: MutableList<String>) {
        if (roots.isEmpty()) {
            errors.add("roots: empty list")
        }
        roots.forEach { root ->
            if (!CACHE_ROOTS_REGEX.matches(root)) {
                errors.add("root '$root': must be under /data, /sdcard, or /storage")
            }
        }
        if (maxDepth < 1 || maxDepth > 10) {
            errors.add("maxDepth: must be 1..10")
        }
    }

    private fun validateFreeBytesHint(hint: String, errors: MutableList<String>) {
        if (hint.isBlank()) {
            errors.add("freeBytesHint: empty")
        } else if (!FREE_BYTES_HINT_REGEX.matches(hint)) {
            errors.add("freeBytesHint: invalid format (expected: e.g., 500M, 1G, 100K)")
        }
    }
}