/*
 * Copyright (C) 2026 Files Manager Contributors.
 *
 * Root shell command execution ported and adapted from Amaze File Manager
 * (https://github.com/TeamAmaze/AmazeFileManager), specifically
 * filesystem/root/base/IRootCommand.kt, filesystem/root/DeleteFileCommand.kt
 * and filesystem/root/MoveFileCommand.kt.
 * Copyright (C) 2014-2026 Arpit Khurana, Vishal Nehra, Emmanuel Messulam,
 * Raymond Lai and Contributors.
 *
 * Amaze's getCommandLineString() sanitizer is deliberately NOT used here: it
 * strips characters outside an ASCII whitelist, which turns a path such as
 * /storage/emulated/0/Документы into /storage/emulated/0/ and would point
 * `rm -rf` at the whole volume. Arguments are POSIX-quoted instead.
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

import com.filemanager.app.util.rootAccessiblePath
import com.filemanager.app.util.shellQuote
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One entry of a directory listed through the root shell. */
data class RootEntry(val name: String, val isDirectory: Boolean)

/**
 * Wraps libsu shell execution for paths plain java.io can't reach — system
 * partitions and other apps' private directories on rooted devices. Used as a
 * fallback after a normal filesystem call fails.
 */
object RootFileOperations {

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun deleteRecursively(target: File): Boolean = withContext(Dispatchers.IO) {
        runCommand("rm -rf ${quotedRootPath(target)}")
    }

    suspend fun move(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        runCommand(
            "mv -f ${quotedRootPath(source)} ${quotedRootPath(destination)}"
        )
    }

    suspend fun copy(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        val recursive = if (source.isDirectory) "-r " else ""
        runCommand(
            "cp $recursive${quotedRootPath(source)} " +
                quotedRootPath(destination)
        )
    }

    suspend fun mkdir(target: File): Boolean = withContext(Dispatchers.IO) {
        runCommand("mkdir -p ${quotedRootPath(target)}")
    }

    suspend fun rename(source: File, destination: File): Boolean = move(source, destination)

    suspend fun chmod(target: File, octalMode: String): Boolean = withContext(Dispatchers.IO) {
        runCommand("chmod $octalMode ${quotedRootPath(target)}")
    }

    /**
     * Lists a directory the app can't read directly. `ls -p` marks directories
     * with a trailing slash, so entries stay navigable even when stat() is
     * denied to the app's own uid.
     *
     * @return the entries, or null when root is unavailable or the command failed.
     */
    suspend fun listDirectory(directory: File): List<RootEntry>? = withContext(Dispatchers.IO) {
        if (!hasRoot()) return@withContext null
        val quoted = quotedRootPath(directory)
        val result = Shell.cmd("ls -Ap $quoted").exec()
        val lines = if (result.isSuccess) result.out else {
            val fallback = Shell.cmd("ls -ap $quoted").exec()
            if (!fallback.isSuccess) return@withContext null
            fallback.out
        }
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." && it != "./" && it != "../" }
            .map { entry ->
                val isDirectory = entry.endsWith("/")
                RootEntry(name = entry.removeSuffix("/"), isDirectory = isDirectory)
            }
            .toList()
    }

    /**
     * Quotes a path for the shell, rewritten to the location root can actually
     * reach: /storage/emulated is a FUSE view that blocks Android/data even for
     * root, while /data/media holds the real files.
     */
    private fun quotedRootPath(file: File): String =
        shellQuote(rootAccessiblePath(file.absolutePath))

    private fun hasRoot(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    private fun runCommand(command: String): Boolean {
        if (!hasRoot()) return false
        return Shell.cmd(command).exec().isSuccess
    }
}
