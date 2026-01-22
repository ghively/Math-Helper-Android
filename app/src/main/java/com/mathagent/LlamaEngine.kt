package com.mathagent

import android.content.Context
import java.io.File

/**
 * JNI wrapper for llama.cpp
 *
 * This class provides native access to llama.cpp for on-device LLM inference.
 *
 * Native library name: "mathagent" (builds from app/src/main/cpp/)
 */
class LlamaEngine(private val context: Context) {

    companion object {
        init {
            System.loadLibrary("mathagent")
        }

        // Model parameters for Qwen2.5-Math-1.5B-Q4_K_M
        private const val N_CTX = 2048          // Context window
        private const val N_GPU_LAYERS = 99    // Offload all to GPU (Vulkan)
        private const val N_THREADS = 8        // Tensor G2 has 8 CPU cores
        private const val MAX_TOKENS = 512     // Max tokens per generation
        private const val TEMPERATURE = 0.7f

        // GBNF Grammar for ReAct tool calling (forces valid JSON)
        private const val REACT_GRAMMAR = """
            root ::= tool_call | final_answer | text
            tool_call ::= "{" ws "action" ws ":" ws quote action quote "," ws "input" ws ":" ws quote input quote "}"
            final_answer ::= "{" ws "answer" ws ":" ws quote text quote "}"
            action ::= "calculate" | "solve_equation" | "simplify_expression" | "get_hint"
            input ::= [^"]*
            text ::= [^"]*
            ws ::= " "?
            quote ::= '"'
        """
    }

    private var modelPtr: Long = 0
    private var ctxPtr: Long = 0
    var isLoaded = false
        private set

    init {
        // Initialize llama.cpp backend
        nativeInit()
    }

    /**
     * Load the GGUF model from file
     *
     * Model: Qwen2.5-Math-1.5B-Instruct-Q4_K_M.gguf (~1GB)
     * Download from: https://huggingface.co/RichardErkhov/Qwen_-_Qwen2.5-Math-1.5B-gguf
     */
    suspend fun loadModel(modelPath: String): Boolean {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found: $modelPath")
        }

        // Load model
        modelPtr = nativeLoadModel(modelPath, N_CTX, N_GPU_LAYERS)
        if (modelPtr == 0L) {
            return false
        }

        // Initialize context
        ctxPtr = nativeInitContext(modelPtr, N_CTX, N_THREADS, TEMPERATURE)
        isLoaded = ctxPtr != 0L

        return isLoaded
    }

    /**
     * Generate completion with streaming
     *
     * @param prompt The input prompt
     * @param grammar Optional GBNF grammar for constrained decoding
     * @param onToken Callback for each generated token
     */
    suspend fun generate(
        prompt: String,
        grammar: String? = REACT_GRAMMAR,
        onToken: (String) -> Unit
    ): String {
        if (!isLoaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        // Create callback wrapper
        val callback = TokenCallback { token ->
            onToken(token)
        }

        val result = nativeGenerate(
            ctxPtr = ctxPtr,
            prompt = prompt,
            maxTokens = MAX_TOKENS,
            temperature = TEMPERATURE,
            grammar = grammar,
            callback = callback
        )

        return result
    }

    /**
     * Get system information (for debugging)
     */
    fun getSystemInfo(): String {
        return nativeSystemInfo()
    }

    /**
     * Free native resources
     */
    fun close() {
        if (ctxPtr != 0L) {
            nativeFreeContext(ctxPtr)
            ctxPtr = 0
        }
        if (modelPtr != 0L) {
            nativeFreeModel(modelPtr)
            modelPtr = 0
        }
        isLoaded = false
    }

    protected fun finalize() {
        close()
    }

    // ==========================================================================
    // Native method declarations (implemented in llama_jni.cpp)
    // ==========================================================================

    private external fun nativeInit()
    private external fun nativeLoadModel(path: String, nCtx: Int, nGpuLayers: Int): Long
    private external fun nativeInitContext(modelPtr: Long, nCtx: Int, nThreads: Int, temperature: Float): Long
    private external fun nativeGenerate(
        ctxPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        grammar: String?,
        callback: TokenCallback
    ): String
    private external fun nativeFreeContext(ctxPtr: Long)
    private external fun nativeFreeModel(modelPtr: Long)
    private external fun nativeSystemInfo(): String
    private external fun nativeShutdown()
}

/**
 * Callback interface for streaming token generation
 */
class TokenCallback(private val onToken: (String) -> Unit) {
    fun onToken(token: String) {
        onToken(token)
    }
}
