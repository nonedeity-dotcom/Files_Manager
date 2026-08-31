package com.filemanager.app.util

import kotlin.math.ln
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups - 1])
}
