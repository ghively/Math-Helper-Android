package com.mathagent

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * SymPy bridge using Chaquopy
 *
 * Provides access to Python symbolic math capabilities
 * through SymPy for algebraic simplification, equation solving, etc.
 */
class SymPyBridge(private val context: Context) {

    private val python: Python by lazy { Python.getInstance() }

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
    suspend fun calculate(expr: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("calculate", expr)
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

    /**
     * Solve an equation
     */
    suspend fun solveEquation(equation: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("solve_equation", equation)
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

    /**
     * Simplify an algebraic expression
     */
    suspend fun simplifyExpression(expr: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("simplify_expression", expr)
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

    /**
     * Expand an algebraic expression
     */
    suspend fun expandExpression(expr: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("expand_expression", expr)
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

    /**
     * Factor an algebraic expression
     */
    suspend fun factorExpression(expr: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("factor_expression", expr)
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

    /**
     * Generate a Socratic hint
     */
    suspend fun getHint(problem: String, lastAttempt: String? = null): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = if (lastAttempt != null) {
                module.callAttr("get_hint", problem, lastAttempt)
            } else {
                module.callAttr("get_hint", problem)
            }
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

    /**
     * Verify a worked example
     */
    suspend fun verifyWorkedExample(problem: String, studentWork: String): SymPyResult = withContext(Dispatchers.IO) {
        try {
            val module = python.getModule("sympy_bridge")
            val result = module.callAttr("verify_worked_example", problem, studentWork)
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
            // Convert Python dict to Kotlin map
            val dict = pyObj as? Map<*, *>
                ?: throw IllegalArgumentException("Expected dict from Python")

            val success = dict["success"] as? Boolean ?: false
            val result = dict["result"] as? String
            val explanation = dict["explanation"] as? String ?: ""
            val error = dict["error"] as? String
            val steps = dict["steps"] as? List<*>?.map { it.toString() }
            val solutions = dict["solutions"] as? List<*>?.map { it.toString() }

            return SymPyResult(
                success = success,
                result = result,
                explanation = explanation,
                error = error,
                steps = steps,
                solutions = solutions
            )
        }
    }

    /**
     * Convert to ToolResult for ReAct agent
     */
    fun toToolResult(): ToolResult {
        return ToolResult(
            success = success,
            result = result,
            explanation = explanation,
            error = error
        )
    }
}

/**
 * Android platform for Chaquopy Python
 */
import com.chaquo.python.android.AndroidPlatform

private class AndroidPlatform(private val context: Context) : com.chaquo.python.Python.Platform {
    override fun getAttribute(name: String?): Any? {
        return when (name) {
            "android.context" -> context
            else -> null
        }
    }
}
