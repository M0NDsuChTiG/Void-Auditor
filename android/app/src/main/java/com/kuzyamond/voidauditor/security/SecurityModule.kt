package com.kuzyamond.voidauditor.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object SecurityModule {

    private const val PREFS_NAME = "void_auditor_secure_prefs"

    fun getEncryptedPrefs(context: Context) = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(context: Context, key: String) {
        getEncryptedPrefs(context).edit().putString("gemini_api_key", key).apply()
    }

    fun getApiKey(context: Context): String? {
        return getEncryptedPrefs(context).getString("gemini_api_key", null)
    }

    fun canAuthenticate(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun createCredentialIntent(activity: Activity): Intent? {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.createConfirmDeviceCredentialIntent(
            "VOID Auditor",
            "Аутентификация для доступа к инструменту"
        )
    }

    fun getSecureLogsDir(context: Context): File {
        val dir = File(context.filesDir, "audit_logs")
        dir.mkdirs()
        return dir
    }
}
