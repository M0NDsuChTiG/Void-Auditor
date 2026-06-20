package com.kuzyamond.voidauditor.security

import androidx.fragment.app.FragmentActivity
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object SecurityModule {

    private const val PREFS_NAME = "void_auditor_secure_prefs"

    /** Encrypted SharedPreferences */
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

    /** Биометрия / PIN при запуске приложения */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError?.invoke(errString.toString())
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("VOID Auditor")
            .setSubtitle("Аутентификация для доступа к инструменту")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /** Безопасное хранение логов аудита (внутренняя память) */
    fun getSecureLogsDir(context: Context): File {
        val dir = File(context.filesDir, "audit_logs")
        dir.mkdirs()
        return dir
    }
}
