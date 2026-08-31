/*
 * Copyright (C) 2026 Files Manager Contributors.
 *
 * Root shell command execution ported and adapted from Amaze File Manager
 * (https://github.com/TeamAmaze/AmazeFileManager), specifically
 * filesystem/root/base/IRootCommand.kt, filesystem/root/DeleteFileCommand.kt,
 * filesystem/root/MoveFileCommand.kt and filesystem/RootHelper.java's
 * getCommandLineString() sanitizer.
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

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps libsu shell execution for operations that plain java.io access can't
 * reach (system partitions, other apps' private dirs on rooted devices).
 * Used as a fallback when a normal filesystem call fails.
 */
object RootFileOperations {

    private val ARG_WHITELIST = Regex("[^a-zA-Z0-9@/:}{\\-_=+.,'\"\\s]")

    /** Strips characters that could break out of a quoted shell argument. */
    private fun sanitize(path: String): String = path.replace(ARG_WHITELIST, "")

    private fun quoted(path: String): String = "\"${sanitize(path)}\""

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun deleteRecursively(target: File): Boolean = withContext(Dispatchers.IO) {
        runCommand("rm -rf ${quoted(target.absolutePath)}")
    }

    suspend fun move(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        runCommand("mv ${quoted(source.absolutePath)} ${quoted(destination.absolutePath)}")
    }

    suspend fun copy(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        val flag = if (source.isDirectory) "-r" else ""
        runCommand("cp $flag ${quoted(source.absolutePath)} ${quoted(destination.absolutePath)}")
    }

    suspend fun mkdir(target: File): Boolean = withContext(Dispatchers.IO) {
        runCommand("mkdir -p ${quoted(target.absolutePath)}")
    }

    suspend fun rename(source: File, destination: File): Boolean =
        move(source, destination)

    suspend fun chmod(target: File, octalMode: String): Boolean = withContext(Dispatchers.IO) {
        runCommand("chmod $octalMode ${quoted(target.absolutePath)}")
    }

    private fun runCommand(command: String): Boolean {
        if (!Shell.getShell().isRoot) return false
        val result = Shell.cmd(command).exec()
        return result.isSuccess
    }
}
