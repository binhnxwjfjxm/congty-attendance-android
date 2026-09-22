package com.hungphat.attendance.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class FaceDeviceConfig(
    val id: String,
    val name: String,
    val branchId: String,
    val branchName: String?,
    val attendancePointId: String,
    val attendancePointName: String?,
    val credential: String,
)

class DeviceCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun load(): FaceDeviceConfig? {
        val encrypted = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val envelope = JSONObject(encrypted)
            val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val payload = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            FaceDeviceConfig(
                id = payload.getString("id"),
                name = payload.getString("name"),
                branchId = payload.getString("branchId"),
                branchName = payload.optString("branchName").takeIf { it.isNotBlank() },
                attendancePointId = payload.getString("attendancePointId"),
                attendancePointName = payload.optString("attendancePointName").takeIf { it.isNotBlank() },
                credential = payload.getString("credential"),
            )
        }.getOrNull()
    }

    fun save(config: FaceDeviceConfig) {
        val payload = JSONObject()
            .put("id", config.id)
            .put("name", config.name)
            .put("branchId", config.branchId)
            .put("branchName", config.branchName ?: "")
            .put("attendancePointId", config.attendancePointId)
            .put("attendancePointName", config.attendancePointName ?: "")
            .put("credential", config.credential)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val envelope = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP))
        preferences.edit().putString(KEY_PAYLOAD, envelope.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_PAYLOAD).apply()
    }

    private fun secretKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "face_device_secure"
        const val KEY_PAYLOAD = "device"
        const val KEY_ALIAS = "attendance_face_device_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
