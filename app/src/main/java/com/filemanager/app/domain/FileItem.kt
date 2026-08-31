package com.filemanager.app.domain

import java.io.File

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val extension: String = file.extension.lowercase()
)

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC
}

enum class FileType {
    FOLDER, IMAGE, TEXT, AUDIO, VIDEO, ARCHIVE, APK, OTHER
}

fun FileItem.type(): FileType = when {
    isDirectory -> FileType.FOLDER
    extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> FileType.IMAGE
    extension in setOf("txt", "md", "json", "xml", "log", "kt", "java", "py", "js", "ts", "csv", "yaml", "yml", "gradle", "properties", "html", "css") -> FileType.TEXT
    extension in setOf("mp3", "wav", "flac", "ogg", "m4a") -> FileType.AUDIO
    extension in setOf("mp4", "mkv", "avi", "mov", "webm") -> FileType.VIDEO
    extension in setOf("zip", "rar", "7z", "tar", "gz") -> FileType.ARCHIVE
    extension == "apk" -> FileType.APK
    else -> FileType.OTHER
}
