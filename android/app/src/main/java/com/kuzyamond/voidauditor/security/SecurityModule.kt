package com.kuzyamond.voidauditor.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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

    fun resolveAuthenticators(): Int {
        return BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    fun canAuthenticate(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(resolveAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onError?.invoke("Аутентификация отменена")
                } else {
                    onError?.invoke(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // Пользователь может повторить — не закрываем gate
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("VOID Auditor")
            .setSubtitle("Аутентификация для доступа к инструменту")
            .setAllowedAuthenticators(resolveAuthenticators())
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    fun getSecureLogsDir(context: Context): File {
        val dir = File(context.filesDir, "audit_logs")
        dir.mkdirs()
        return dir
    }
}
