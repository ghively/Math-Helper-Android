package com.mathagent

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyException
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SymPy bridge using Chaquopy
 *
 * Provides access to Python symbolic math capabilities
 * through SymPy for algebraic simplification, equation solving, etc.
 */
class SymPyBridge(private val context: Context) {

    private val python: Python by lazy { Python.getInstance() }

    // Cache the module reference to avoid repeated lookups
    private val sympyModule by lazy { python.getModule("sympy_bridge") }

    /**
     * Initialize Python environment
     */
    fun init() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    /**
     * Calculate a numeric expression
     */
    suspend fun calculate(expr: String): SymPyResult =
        invokeMethod("calculate", expr)

    /**
     * Solve an equation
     */
    suspend fun solveEquation(equation: String): SymPyResult =
        invokeMethod("solve_equation", equation)

    /**
     * Simplify an algebraic expression
     */
    suspend fun simplifyExpression(expr: String): SymPyResult =
        invokeMethod("simplify_expression", expr)

    /**
     * Expand an algebraic expression
     */
    suspend fun expandExpression(expr: String): SymPyResult =
        invokeMethod("expand_expression", expr)

    /**
     * Factor an algebraic expression
     */
    suspend fun factorExpression(expr: String): SymPyResult =
        invokeMethod("factor_expression", expr)

    /**
     * Generate a Socratic hint
     */
    suspend fun getHint(problem: String, lastAttempt: String? = null): SymPyResult {
        return if (lastAttempt != null) {
            invokeMethod("get_hint", problem, lastAttempt)
        } else {
            invokeMethod("get_hint", problem)
        }
    }

    /**
     * Verify a worked example
     */
    suspend fun verifyWorkedExample(problem: String, studentWork: String): SymPyResult =
        invokeMethod("verify_worked_example", problem, studentWork)

    // ==========================================================================
    // Helper methods
    // ==========================================================================

    /**
     * Generic method invoker to reduce boilerplate
     */
    private suspend fun invokeMethod(
        methodName: String,
        vararg args: Any?
    ): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val result = sympyModule.callAttr(methodName, *args)
            SymPyResult.fromPyObject(result)
        } catch (e: PyException) {
            SymPyResult(
                success = false,
                result = null,
                explanation = "Python error: ${e.message}",
                error = e.message
            )
        }
    }
}

/**
 * Result from SymPy operations
 */
data class SymPyResult(
    val success: Boolean,
    val result: String?,
    val explanation: String,
    val error: String? = null,
    val steps: List<String>? = null,
    val solutions: List<String>? = null
) {
    companion object {
        /**
         * Parse Python dict result to SymPyResult
         */
        fun fromPyObject(pyObj: Any): SymPyResult {
            val dict = pyObj as? Map<*, *>
                ?: return SymPyResult(
                    success = false,
                    result = null,
                    explanation = "Invalid response from Python",
                    error = "Expected dict from Python"
                )

            return SymPyResult(
                success = dict["success"] as? Boolean ?: false,
                result = dict["result"] as? String,
                explanation = dict["explanation"] as? String ?: "",
                error = dict["error"] as? String,
                steps = (dict["steps"] as? List<*>)?.map { it.toString() },
                solutions = (dict["solutions"] as? List<*>)?.map { it.toString() }
            )
        }
    }

    /**
     * Convert to ToolResult for ReAct agent
     */
    fun toToolResult(): ToolResult = ToolResult(
        success = success,
        result = result,
        explanation = explanation,
        error = error
    )
}

/**
 * Android platform for Chaquopy Python
 */
private class AndroidPlatform(private val context: Context) : Python.Platform {
    override fun getAttribute(name: String?): Any? = when (name) {
        "android.context" -> context
        else -> null
    }
}
