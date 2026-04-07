package com.citrus.blsc.utils

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object AppSecurityManager {
    private const val PREFS_NAME = "secure_device_binding_prefs"
    private const val KEY_BOUND_DEVICE_HASH = "bound_device_hash"
    private const val DEVICE_SALT = "blsc_offline_bind_v1"

    fun verifyOrBindDevice(context: Context): Boolean {
        val securePrefs = getSecurePrefs(context)
        val currentDeviceHash = hashDeviceId(getAndroidId(context))
        val savedDeviceHash = securePrefs.getString(KEY_BOUND_DEVICE_HASH, null)

        if (savedDeviceHash == null) {
            securePrefs.edit().putString(KEY_BOUND_DEVICE_HASH, currentDeviceHash).apply()
            return true
        }

        return savedDeviceHash == currentDeviceHash
    }

    private fun getSecurePrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_android_id"
    }

    private fun hashDeviceId(androidId: String): String {
        val input = "$DEVICE_SALT:$androidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

}

