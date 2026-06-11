package com.capyreader.app.articleimages

import android.content.Context
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ArticleImageStore(context: Context) {
    private val root = File(context.filesDir, ROOT_DIR)

    fun write(
        assetID: String,
        mimeType: String,
        sourceURL: String,
        body: ResponseBody,
        maxBytes: Long = MAX_IMAGE_BYTES,
    ): StoredArticleImage {
        val relativePath = relativePath(assetID, mimeType, sourceURL)
        val target = fileForRelativePath(relativePath)
            ?: throw IOException("Invalid article image path")
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

                        bytesWritten += read
                        if (bytesWritten > maxBytes) {
                            throw ImageTooLargeException(maxBytes)
                        }

                        output.write(buffer, 0, read)
                    }
                }
            }

            moveIntoPlace(temp, target)
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }

        return StoredArticleImage(
            relativePath = relativePath,
            byteSize = bytesWritten,
        )
    }

    fun fileForRelativePath(relativePath: String): File? {
        val rootFile = root.canonicalFile
        val file = File(rootFile, relativePath.trimStart('/')).canonicalFile

        if (!file.toPath().startsWith(rootFile.toPath())) {
            return null
        }

        return file
    }

    fun fileForAssetID(assetID: String): File? {
        if (assetID.length < 2) {
            return null
        }

        val shard = File(root, assetID.take(2))

        return shard
            .listFiles { file ->
                file.isFile &&
                    file.name.startsWith("$assetID.") &&
                    !file.name.endsWith(TEMP_EXTENSION)
            }
            ?.firstOrNull()
    }

    fun delete(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) {
            return false
        }

        val file = fileForRelativePath(relativePath) ?: return false

        return !file.exists() || file.delete()
    }

    fun deleteAll() {
        if (root.exists()) {
            root.deleteRecursively()
        }
    }

    fun files(): List<ArticleImageFile> {
        val rootFile = root.canonicalFile
        if (!rootFile.exists()) {
            return emptyList()
        }

        return rootFile
            .walkTopDown()
            .filter { it.isFile }
            .filterNot { it.name.endsWith(TEMP_EXTENSION) }
            .mapNotNull { file ->
                val relativePath = file.canonicalFile
                    .relativeToOrNull(rootFile)
                    ?.invariantSeparatorsPath
                    ?: return@mapNotNull null

                ArticleImageFile(
                    relativePath = relativePath,
                    byteSize = file.length(),
                )
            }
            .toList()
    }

    private fun relativePath(assetID: String, mimeType: String, sourceURL: String): String {
        return "${assetID.take(2)}/$assetID${extension(mimeType, sourceURL)}"
    }

    private fun extension(mimeType: String, sourceURL: String): String {
        return when (mimeType.substringBefore(";").trim().lowercase()) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "image/avif" -> ".avif"
            "image/svg+xml" -> ".svg"
            else -> sourceURL
                .substringBefore("?")
                .substringBefore("#")
                .substringAfterLast("/", "")
                .let { filename ->
                    val ext = filename.substringAfterLast(".", "").lowercase()
                    if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) {
                        ".$ext"
                    } else {
                        ".img"
                    }
                }
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

    class ImageTooLargeException(maxBytes: Long) : IOException("Image exceeds $maxBytes bytes")

    companion object {
        private const val ROOT_DIR = "article-images"
        private const val TEMP_EXTENSION = ".tmp"
        const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
    }
}

data class StoredArticleImage(
    val relativePath: String,
    val byteSize: Long,
)

data class ArticleImageFile(
    val relativePath: String,
    val byteSize: Long,
)
