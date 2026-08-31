# Third-party attribution

This project is licensed under the GNU General Public License v3.0 (see `LICENSE`).

Some functionality was ported or adapted from the following GPL-3.0 open-source
Android file managers. Their copyright notices are preserved in the source files
that derive from them.

## Amaze File Manager
- https://github.com/TeamAmaze/AmazeFileManager
- License: GPL-3.0
- Copyright (C) 2014-2026 Arpit Khurana, Vishal Nehra, Emmanuel Messulam,
  Raymond Lai and Contributors.
- Ported/adapted:
  - Root shell command execution pattern (`RootFileOperations.kt`), based on
    `filesystem/root/base/IRootCommand.kt`, `filesystem/root/DeleteFileCommand.kt`,
    `filesystem/root/MoveFileCommand.kt`, and `filesystem/RootHelper.java`
    (command-line argument sanitization, shell result handling).
  - AndroidKeyStore-backed AES-GCM encryption (`VaultCrypto.kt`), based on
    `utils/PasswordUtil.kt` and `utils/security/SecretKeygen.kt`.

## Material Files
- https://github.com/zhanghai/MaterialFiles
- License: GPL-3.0
- Copyright (C) Hai Zhang and Contributors.
- Referenced for hidden-file visibility toggle conventions and privacy-oriented
  settings UX patterns.

## Ghost Commander
- https://github.com/PDi-Communication-Systems-Inc/ghostcommander
  (mirror of the original SourceForge project)
- License: GPL-3.0
- Referenced for dual-pane/root-explorer UX conventions informing the
  root-access settings screen.

All ported code was rewritten in Kotlin to fit this project's Compose/MVVM
architecture; it is not a byte-for-byte copy of the original files.
