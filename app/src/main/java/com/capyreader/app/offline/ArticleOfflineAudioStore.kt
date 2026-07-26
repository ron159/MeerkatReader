package com.capyreader.app.offline

import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class ArticleOfflineAudioStore(accountDirectory: File) {
    private val root = File(accountDirectory, ROOT_DIR)

    fun write(
        articleID: String,
        sourceURL: String,
        mimeType: String,
        body: ResponseBody,
    ): Long {
        val target = file(articleID, sourceURL, mimeType)
        val temp = File(target.parentFile, "${target.name}$TEMP_EXTENSION")

        target.parentFile?.mkdirs()

        var bytesWritten = 0L
        try {
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }

                        output.write(buffer, 0, read)
                        bytesWritten += read
                    }
                }
            }

            moveIntoPlace(temp, target)
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }

        return bytesWritten
    }

    fun localURL(
        articleID: String,
        sourceURL: String,
        mimeType: String,
    ): String? {
        val file = file(articleID, sourceURL, mimeType)

        return file
            .takeIf { it.isFile }
            ?.toURI()
            ?.toString()
    }

    fun retain(
        articleID: String,
        enclosures: Collection<OfflineAudioEnclosure>,
    ): Long {
        val directory = articleDirectory(articleID)
        if (!directory.exists()) {
            return 0L
        }

        val expectedNames = enclosures
            .map { enclosure ->
                file(
                    articleID = articleID,
                    sourceURL = enclosure.sourceURL,
                    mimeType = enclosure.mimeType,
                ).name
            }
            .toSet()

        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name !in expectedNames }
            .forEach(File::delete)

        if (expectedNames.isEmpty()) {
            directory.deleteRecursively()
            return 0L
        }

        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(TEMP_EXTENSION) }
            .sumOf(File::length)
    }

    fun deleteArticle(articleID: String) {
        articleDirectory(articleID).deleteRecursively()
    }

    fun deleteAll() {
        if (root.exists() && !root.deleteRecursively()) {
            throw IOException("Could not clear offline audio")
        }
    }

    fun deleteUnreferencedArticles(articleIDs: Collection<String>) {
        if (!root.exists()) {
            return
        }

        val referencedDirectories = articleIDs
            .map(::articleDirectoryName)
            .toSet()

        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name !in referencedDirectories }
            .forEach(File::deleteRecursively)
    }

    private fun file(
        articleID: String,
        sourceURL: String,
        mimeType: String,
    ): File {
        return File(
            articleDirectory(articleID),
            "${sha256(sourceURL)}${extension(mimeType, sourceURL)}",
        )
    }

    private fun articleDirectory(articleID: String): File {
        return File(root, articleDirectoryName(articleID))
    }

    private fun articleDirectoryName(articleID: String): String = sha256(articleID)

    private fun extension(mimeType: String, sourceURL: String): String {
        return when (mimeType.substringBefore(";").trim().lowercase()) {
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/aac" -> ".aac"
            "audio/ogg" -> ".ogg"
            "audio/opus" -> ".opus"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/webm" -> ".webm"
            else -> sourceURL
                .substringBefore("?")
                .substringBefore("#")
                .substringAfterLast("/", "")
                .substringAfterLast(".", "")
                .lowercase()
                .takeIf { extension ->
                    extension.length in 2..5 &&
                        extension.all { it.isLetterOrDigit() }
                }
                ?.let { ".$it" }
                ?: ".audio"
        }
    }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val ROOT_DIR = "offline-audio"
        private const val TEMP_EXTENSION = ".tmp"
    }
}

data class OfflineAudioEnclosure(
    val sourceURL: String,
    val mimeType: String,
)
