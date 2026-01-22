package com.mathagent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages model download and storage
 *
 * Supports downloading from:
 * - GitHub releases
 * - HuggingFace (direct or custom URLs)
 * - Any direct GGUF URL
 */
class ModelManager(private val context: Context) {

    companion object {
        // Default model
        private const val DEFAULT_MODEL_NAME = "Qwen2.5-Math-1.5B.Q4_K_M.gguf"
        private const val DEFAULT_MODEL_SIZE = 986048448L // ~940MB

        /**
         * Registry of available models from HuggingFace
         */
        val AVAILABLE_MODELS = listOf(
            ModelOption(
                id = "qwen2.5-math-1.5b-q4",
                name = "Qwen2.5-Math 1.5B (Q4_K_M)",
                description = "Recommended - Best balance of speed and accuracy. 79.7% MATH benchmark.",
                sizeBytes = 986048448,
                url = "https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf/resolve/main/Qwen2.5-Math-1.5B.Q4_K_M.gguf",
                recommended = true
            ),
            ModelOption(
                id = "qwen2.5-math-1.5b-q5",
                name = "Qwen2.5-Math 1.5B (Q5_K_M)",
                description = "Higher accuracy, slightly slower. ~1.1GB",
                sizeBytes = 1175331840,
                url = "https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf/resolve/main/Qwen2.5-Math-1.5B.Q5_K_M.gguf",
                recommended = false
            ),
            ModelOption(
                id = "qwen2.5-math-1.5b-q3",
                name = "Qwen2.5-Math 1.5B (Q3_K_M)",
                description = "Faster, smaller. ~780MB. Good for older devices.",
                sizeBytes = 817889280,
                url = "https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf/resolve/main/Qwen2.5-Math-1.5B.Q3_K_M.gguf",
                recommended = false
            ),
            ModelOption(
                id = "qwen2-math-1.5b-q4",
                name = "Qwen2-Math 1.5B (Q4_K_M)",
                description = "Previous generation. Still capable, slightly faster.",
                sizeBytes = 928514048,
                url = "https://huggingface.co/RichardErkhov/Qwen_-_qwen2-1.5b-math-instruct-gguf/resolve/main/Qwen2-1.5B-Math-instruct-q4_k_m.gguf",
                recommended = false
            )
        )
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Get the currently active model file
     */
    val activeModelFile: File
        get() {
            // Check for downloaded models, prefer default
            val defaultFile = File(modelsDir, DEFAULT_MODEL_NAME)
            if (defaultFile.exists()) return defaultFile

            // Return any downloaded GGUF file
            modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }?.let {
                return it
            }

            // Fallback to default path (may not exist yet)
            return defaultFile
        }

    /**
     * Get all downloaded models
     */
    fun getDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.extension == "gguf" } ?: emptyList()
    }

    /**
     * Check if any model is downloaded
     */
    fun isAnyModelDownloaded(): Boolean {
        return getDownloadedModels().isNotEmpty()
    }

    /**
     * Check if a specific model is downloaded
     */
    fun isModelDownloaded(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 1_000_000 // At least 1MB
    }

    /**
     * Download model from a direct URL
     *
     * @param url Direct download URL to GGUF file
     * @param fileName Optional custom filename (auto-detected from URL if null)
     * @param expectedSize Optional expected size for progress calculation
     * @param onProgress Progress callback (bytes downloaded, total bytes)
     */
    suspend fun downloadFromUrl(
        url: String,
        fileName: String? = null,
        expectedSize: Long? = null,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFileName = fileName ?: url.substringAfterLast("/")
            val outputFile = File(modelsDir, "$targetFileName.part")
            val finalFile = File(modelsDir, targetFileName)

            // Delete partial download if exists
            if (outputFile.exists()) outputFile.delete()

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 60000  // Longer timeout for large files
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "MathMentor-Android/1.0")
            }

            // Check response code
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                )
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength > 0) contentLength else (expectedSize ?: DEFAULT_MODEL_SIZE)

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        onProgress?.invoke(totalBytesRead, totalBytes)
                    }
                }
            }

            // Rename to final filename
            if (outputFile.renameTo(finalFile)) {
                Result.success(finalFile)
            } else {
                Result.failure(Exception("Failed to complete download"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a model file
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Get download progress for a specific model
     */
    fun getDownloadProgress(fileName: String): Float {
        val file = File(modelsDir, "$fileName.part")
        if (!file.exists()) return 0f

        // Estimate progress based on typical model size
        val estimatedSize = when {
            fileName.contains("Q5") -> 1_200_000_000L
            fileName.contains("Q4") -> 1_000_000_000L
            fileName.contains("Q3") -> 850_000_000L
            else -> DEFAULT_MODEL_SIZE
        }

        return (file.length().toFloat() / estimatedSize).coerceAtMost(1f)
    }

    /**
     * Get info about all downloaded models
     */
    fun getModelsInfo(): List<ModelInfo> {
        return getDownloadedModels().map { file ->
            val matchingOption = AVAILABLE_MODELS.find {
                it.url.substringAfterLast("/") == file.name
            }

            ModelInfo(
                fileName = file.name,
                fileSize = file.length(),
                downloaded = true,
                currentSize = file.length(),
                downloadUrl = matchingOption?.url ?: "Unknown",
                displayName = matchingOption?.name ?: file.name,
                description = matchingOption?.description ?: "Custom model"
            )
        }
    }
}

/**
 * Represents a downloadable model option
 */
data class ModelOption(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val url: String,
    val recommended: Boolean = false
) {
    val sizeMB: Double get() = sizeBytes / (1024.0 * 1024.0)
    val sizeDisplay: String get() = "%.1f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
}

/**
 * Information about a model file
 */
data class ModelInfo(
    val fileName: String,
    val fileSize: Long,
    val downloaded: Boolean,
    val currentSize: Long,
    val downloadUrl: String,
    val displayName: String = fileName,
    val description: String = ""
) {
    val sizeMB: Double get() = fileSize / (1024.0 * 1024.0)
    val progress: Float get() = (currentSize.toFloat() / fileSize).coerceAtMost(1f)
    val sizeDisplay: String get() = "%.1f GB".format(fileSize / (1024.0 * 1024.0 * 1024.0))
}
