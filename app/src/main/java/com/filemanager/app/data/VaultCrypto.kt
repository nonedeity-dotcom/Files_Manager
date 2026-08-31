/*
 * Copyright (C) 2026 Files Manager Contributors.
 *
 * AndroidKeyStore-backed AES-GCM encryption ported and adapted from
 * Amaze File Manager (https://github.com/TeamAmaze/AmazeFileManager):
 * utils/PasswordUtil.kt and utils/security/SecretKeygen.kt.
 * Copyright (C) 2014-2026 Arpit Khurana, Vishal Nehra, Emmanuel Messulam,
 * Raymond Lai and Contributors.
 *
 * This file is part of Files Manager.
 *
 * Files Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.filemanager.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts files into the app's private vault directory using an
 * AES-256 key that never leaves the AndroidKeyStore. Files are stored with a
 * random 12-byte GCM IV prefixed to the ciphertext so a stolen file (or
 * removed SD card) is unreadable without this device's keystore.
 */
object VaultCrypto {

    private const val KEY_STORE_ANDROID = "AndroidKeyStore"
    private const val KEY_ALIAS = "files_manager_vault_key"
    private const val ALGO_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    private fun getOrCreateKey(): Key {
        val keyStore = KeyStore.getInstance(KEY_STORE_ANDROID)
        keyStore.load(null)
        keyStore.getKey(KEY_ALIAS, null)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_ANDROID)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    suspend fun encryptFile(source: File, destination: File): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(ALGO_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv

        destination.outputStream().use { out ->
            out.write(iv)
            source.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val encrypted = cipher.update(buffer, 0, read)
                    if (encrypted != null) out.write(encrypted)
                }
                cipher.doFinal()?.let { out.write(it) }
            }
        }
    }

    suspend fun decryptFile(source: File, destination: File): Unit = withContext(Dispatchers.IO) {
        source.inputStream().use { input ->
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            require(input.read(iv) == GCM_IV_LENGTH_BYTES) { "Vault file is corrupted" }

            val cipher = Cipher.getInstance(ALGO_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            destination.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val decrypted = cipher.update(buffer, 0, read)
                    if (decrypted != null) out.write(decrypted)
                }
                cipher.doFinal()?.let { out.write(it) }
            }
        }
    }
}
