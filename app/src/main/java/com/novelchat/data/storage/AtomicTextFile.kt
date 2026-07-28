package com.novelchat.data.storage

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

object AtomicTextFile {
    private const val TEMP_SUFFIX = ".tmp"
    private const val BACKUP_SUFFIX = ".bak"

    @Synchronized
    fun readOrNull(file: File): String? {
        recoverInterruptedWrite(file)
        if (!file.isFile) return null
        return file.readText(StandardCharsets.UTF_8)
    }

    @Synchronized
    fun read(file: File): String {
        return readOrNull(file) ?: throw FileNotFoundException(file.absolutePath)
    }

    @Synchronized
    fun write(file: File, content: String) {
        val parent = file.parentFile
            ?: throw IOException("File has no parent directory: ${file.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create directory: ${parent.absolutePath}")
        }

        recoverInterruptedWrite(file)
        val temp = sibling(file, TEMP_SUFFIX)
        val backup = sibling(file, BACKUP_SUFFIX)
        if (temp.exists() && !temp.delete()) {
            throw IOException("Could not clear temporary file: ${temp.absolutePath}")
        }

        FileOutputStream(temp).use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }

        val hadTarget = file.exists()
        if (hadTarget) {
            if (backup.exists() && !backup.delete()) {
                temp.delete()
                throw IOException("Could not clear backup file: ${backup.absolutePath}")
            }
            if (!file.renameTo(backup)) {
                temp.delete()
                throw IOException("Could not stage existing file: ${file.absolutePath}")
            }
        }

        if (!temp.renameTo(file)) {
            if (hadTarget && backup.exists()) {
                backup.renameTo(file)
            }
            temp.delete()
            throw IOException("Could not replace file: ${file.absolutePath}")
        }

        if (backup.exists()) backup.delete()
    }

    @Synchronized
    fun recoverDirectory(directory: File) {
        if (!directory.isDirectory) return
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.name.endsWith(BACKUP_SUFFIX) || it.name.endsWith(TEMP_SUFFIX) }
            ?.map { transactionFile ->
                val suffix = if (transactionFile.name.endsWith(BACKUP_SUFFIX)) {
                    BACKUP_SUFFIX
                } else {
                    TEMP_SUFFIX
                }
                File(directory, transactionFile.name.removeSuffix(suffix))
            }
            ?.distinctBy { it.absolutePath }
            ?.forEach(::recoverInterruptedWrite)
    }

    private fun recoverInterruptedWrite(file: File) {
        val backup = sibling(file, BACKUP_SUFFIX)
        val temp = sibling(file, TEMP_SUFFIX)

        if (backup.exists()) {
            if (file.exists()) {
                backup.delete()
            } else if (!backup.renameTo(file)) {
                throw IOException("Could not restore backup: ${backup.absolutePath}")
            }
        }
        if (file.exists() && temp.exists()) {
            temp.delete()
        } else if (!file.exists() && temp.isFile && !temp.renameTo(file)) {
            throw IOException("Could not restore temporary file: ${temp.absolutePath}")
        }
    }

    private fun sibling(file: File, suffix: String): File {
        return File(file.parentFile, file.name + suffix)
    }
}
