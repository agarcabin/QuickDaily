package com.quickdaily.util

import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Verify a directory without changing any note or creating a missing vault. Called on IO. */
internal object LocalVaultAccess {
    fun verify(path: String): Boolean {
        val directory = File(path)
        if (!directory.isDirectory || !directory.canRead() || !directory.canWrite()) return false
        val probe = File(directory, ".quickdaily-access-${UUID.randomUUID()}.tmp")
        val bytes = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        var created = false
        var verified = false
        try {
            created = probe.createNewFile()
            if (!created) return false
            FileOutputStream(probe).use { output -> output.write(bytes); output.fd.sync() }
            verified = probe.readBytes().contentEquals(bytes)
        } catch (_: Exception) {
            verified = false
        } finally {
            if (created && !probe.delete()) verified = false
        }
        return verified
    }
}
