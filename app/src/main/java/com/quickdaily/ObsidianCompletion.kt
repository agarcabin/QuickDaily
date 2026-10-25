package com.quickdaily

import android.content.Context
import com.quickdaily.util.VaultStoragePrefs

object ObsidianCompletion {
    fun link(context: Context, candidate: WikilinkCandidate, targetPath: String?): String? = runCatching {
        val vault = VaultStoragePrefs.current(context).rootPath
        val rules = ObsidianConfigSync.rules(context, vault)
        val known = ObsidianFileIndex.paths(vault)
        val target = ObsidianLinkPolicy.noteTarget(candidate.targetPath, known)
        ObsidianLinkPolicy.link(ObsidianConfigSync.sourceNote(context, vault, targetPath, rules), target, rules,
            embed = false, label = candidate.alias ?: candidate.targetPath.substringAfterLast('/'),
            knownPaths = known)
    }.getOrNull()
}
