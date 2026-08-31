package com.filemanager.app.data

import android.content.Context
import com.filemanager.app.domain.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores files encrypted (see [VaultCrypto]) in the app's private storage,
 * which other apps and a plain file browser can't read even with
 * MANAGE_EXTERNAL_STORAGE. The original file is deleted once safely encrypted.
 */
class VaultRepository(context: Context) {

    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }

    suspend fun listVaultItems(): List<FileItem> = withContext(Dispatchers.IO) {
        (vaultDir.listFiles() ?: emptyArray())
            .filter { it.extension == VAULT_EXTENSION }
            .map { FileItem(it, name = it.nameWithoutExtension) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun addToVault(source: File): Boolean = withContext(Dispatchers.IO) {
        if (!source.isFile) return@withContext false
        val encrypted = File(vaultDir, "${source.name}.$VAULT_EXTENSION")
        runCatching {
            VaultCrypto.encryptFile(source, encrypted)
            source.delete()
        }.onFailure { encrypted.delete() }
            .isSuccess
    }

    suspend fun restoreFromVault(vaultFile: File, destinationDir: File): Boolean =
        withContext(Dispatchers.IO) {
            val originalName = vaultFile.name.removeSuffix(".$VAULT_EXTENSION")
            val destination = File(destinationDir, originalName)
            runCatching {
                VaultCrypto.decryptFile(vaultFile, destination)
                vaultFile.delete()
            }.onFailure { destination.delete() }
                .isSuccess
        }

    suspend fun deleteFromVault(vaultFile: File): Boolean = withContext(Dispatchers.IO) {
        vaultFile.delete()
    }

    companion object {
        private const val VAULT_EXTENSION = "vault"
    }
}
