package com.filemanager.app.data

import android.os.Environment
import java.io.File

/** A place the browser can jump to: internal storage, a card, or the system root. */
data class StorageRoot(val label: String, val directory: File)

/**
 * Storage entry points. Internal storage is where browsing starts; the system
 * root and any removable volumes are reachable from the menu, so the app isn't
 * limited to /storage/emulated/0.
 */
object StorageVolumes {

    fun internalStorage(): File = Environment.getExternalStorageDirectory()

    fun roots(): List<StorageRoot> {
        val internal = internalStorage()
        val roots = mutableListOf(
            StorageRoot("Внутренняя память", internal),
            StorageRoot("Системный корень (/)", File("/"))
        )

        // Removable volumes show up as siblings under /storage; "self" and
        // "emulated" are the internal volume's own plumbing.
        File("/storage").listFiles()
            ?.filter { it.isDirectory && it.name != "self" && it.name != "emulated" }
            ?.sortedBy { it.name }
            ?.forEach { volume ->
                if (volume.absolutePath != internal.absolutePath) {
                    roots += StorageRoot(volume.name, volume)
                }
            }

        return roots
    }
}
