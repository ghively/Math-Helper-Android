package com.mathagent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder

/**
 * Math tools for the Socratic Math Tutor
 *
 * Uses exp4j for numeric calculations and SymPy (via Chaquopy)
 * for symbolic algebra operations.
 *
 * These tools are called by the ReAct agent during math reasoning.
 */
class MathTools(private val context: Context) {

    private val sympy: SymPyBridge by lazy {
        SymPyBridge(context).also { it.init() }
    }

    /**
     * Calculate a mathematical expression (numeric)
     *
     * Examples:
     * - "2 + 2" → "4"
     * - "3.5 * 4" → "14"
     * - "sin(30 degrees)" → "0.5"
     */
    suspend fun calculate(input: String): ToolResult {
        return try {
            val processed = preprocessTrig(input)
            val expression: Expression = ExpressionBuilder(processed).build()
            val result = expression.evaluate()

            ToolResult(
                success = true,
                result = formatResult(result),
                explanation = "Calculated: $input = ${formatResult(result)}"
            )
        } catch (e: Exception) {
            // Fallback to SymPy for more complex expressions
            try {
                sympy.calculate(input).toToolResult()
            } catch (e2: Exception) {
                ToolResult(
                    success = false,
                    result = null,
                    explanation = "Error calculating '$input': ${e.message}",
                    error = e.message
                )
            }
        }
    }

    /**
     * Solve an equation
     *
     * Examples:
     * - "2x + 5 = 15" → "x = 5"
     * - "x^2 - 4 = 0" → "x = -2, x = 2"
     */
    suspend fun solveEquation(equation: String): ToolResult {
        return try {
            // Use SymPy for robust equation solving
            sympy.solveEquation(equation).toToolResult()
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error solving equation: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Simplify an algebraic expression (using SymPy)
     *
     * Examples:
     * - "2x + 3x" → "5x"
     * - "(x+1)^2" → "x^2 + 2x + 1"
     * - "3(x + 2) + 4" → "3x + 10"
     */
    suspend fun simplifyExpression(expression: String): ToolResult {
        return try {
            sympy.simplifyExpression(expression).toToolResult()
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error simplifying: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Expand an algebraic expression (using SymPy)
     *
     * Examples:
     * - "(x+1)^2" → "x^2 + 2x + 1"
     * - "2(x+3)" → "2x + 6"
     * - "(x+1)(x+2)" → "x^2 + 3x + 2"
     */
    suspend fun expandExpression(expression: String): ToolResult {
        return try {
            sympy.expandExpression(expression).toToolResult()
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error expanding: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Factor an algebraic expression (using SymPy)
     *
     * Examples:
     * - "x^2 - 4" → "(x - 2)(x + 2)"
     * - "x^2 + 2x + 1" → "(x + 1)^2"
     * - "2x + 4" → "2(x + 2)"
     */
    suspend fun factorExpression(expression: String): ToolResult {
        return try {
            sympy.factorExpression(expression).toToolResult()
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error factoring: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Generate a Socratic hint for a math problem
     *
     * This tool provides pedagogical hints without giving away the answer,
     * following the Socratic method of teaching by questioning.
     *
     * Examples:
     * - "solve 2x + 5 = 13" → "What's the goal when solving an equation?"
     * - "simplify 2x + 3x" → "What do you notice about these terms?"
     */
    suspend fun getHint(problem: String, lastAttempt: String? = null): ToolResult {
        return try {
            sympy.getHint(problem, lastAttempt).toToolResult()
        } catch (e: Exception) {
            // Fallback to basic hints
            ToolResult(
                success = true,
                result = generateHint(problem, lastAttempt),
                explanation = "Generated hint for: $problem"
            )
        }
    }

    /**
     * Verify a student's worked example
     *
     * Reviews step-by-step work and provides feedback.
     *
     * Examples:
     * - problem: "2x + 5 = 13"
     * - work: "2x + 5 = 13\n2x = 8\nx = 4"
     */
    suspend fun verifyWorkedExample(problem: String, studentWork: String): ToolResult {
        return try {
            sympy.verifyWorkedExample(problem, studentWork).toToolResult()
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error verifying work: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Check if an answer is correct
     *
     * Compares student answer with expected result.
     */
    suspend fun checkAnswer(problem: String, studentAnswer: String): ToolResult {
        return try {
            // Try to solve the problem and compare
            val expectedResult = if (problem.contains("=")) {
                solveEquation(problem).result
            } else {
                calculate(problem).result
            }

            val isCorrect = when {
                expectedResult == null -> false
                studentAnswer.contains(expectedResult) -> true
                normalizeAnswer(studentAnswer) == normalizeAnswer(expectedResult) -> true
                else -> false
            }

            if (isCorrect) {
                ToolResult(
                    success = true,
                    result = "Correct!",
                    explanation = "Your answer is correct."
                )
            } else {
                ToolResult(
                    success = true,
                    result = "Not quite",
                    explanation = "Your answer: $studentAnswer. Expected: $expectedResult. Try again!"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error checking answer: ${e.message}",
                error = e.message
            )
        }
    }

    // ==========================================================================
    // Helper functions
    // ==========================================================================

    private fun preprocessTrig(input: String): String {
        // Convert "sin(30)" with degrees to radians for exp4j
        return input
            .replace(Regex("""sin\((\d+)\)""")) { match ->
                "sin(toRadians(${match.groupValues[1]}))"
            }
            .replace(Regex("""cos\((\d+)\)""")) { match ->
                "cos(toRadians(${match.groupValues[1]}))"
            }
            .replace(Regex("""tan\((\d+)\)""")) { match ->
                "tan(toRadians(${match.groupValues[1]}))"
            }
    }

    private fun formatResult(result: Double): String {
        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format("%.4f", result).trimEnd('0').trimEnd('.')
        }
    }

    private fun normalizeAnswer(answer: String): String {
        return answer
            .replace(" ", "")
            .lowercase()
            .replace("x=", "")
            .replace("=", "")
    }

    private fun generateHint(problem: String, lastAttempt: String?): String {
        return when {
            problem.contains("=") -> "What's the first step to isolate the variable?"
            problem.contains("+") || problem.contains("-") → "Try combining like terms first."
            problem.contains("*") || problem.contains("/") → "Remember the order of operations (PEMDAS)."
            "simplify" in problem.lowercase() || "expand" in problem.lowercase() -> "Look for common patterns you can apply."
            "factor" in problem.lowercase() → "What do these terms have in common?"
            "graph" in problem.lowercase() || "plot" in problem.lowercase() -> "What shape would this graph have?"
            lastAttempt != null → "Your answer: $lastAttempt. Check each step carefully."
            else → "What information do you know? What are you trying to find?"
        }
    }
}

/**
 * Companion object for backwards compatibility with non-context usage
 */
object MathTools {
    /**
     * Calculate without context (uses exp4j only)
     */
    fun calculate(input: String): ToolResult {
        return try {
            val processed = preprocessTrig(input)
            val expression: Expression = ExpressionBuilder(processed).build()
            val result = expression.evaluate()

            ToolResult(
                success = true,
                result = formatResult(result),
                explanation = "Calculated: $input = ${formatResult(result)}"
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                result = null,
                explanation = "Error calculating '$input': ${e.message}",
                error = e.message
            )
        }
    }

    private fun preprocessTrig(input: String): String {
        return input
            .replace(Regex("""sin\((\d+)\)""")) { match ->
                "sin(toRadians(${match.groupValues[1]}))"
            }
            .replace(Regex("""cos\((\d+)\)""")) { match ->
                "cos(toRadians(${match.groupValues[1]}))"
            }
            .replace(Regex("""tan\((\d+)\)""")) { match ->
                "tan(toRadians(${match.groupValues[1]}))"
            }
    }

    private fun formatResult(result: Double): String {
        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format("%.4f", result).trimEnd('0').trimEnd('.')
        }
    }
}

/**
 * Result type for tool calls
 */
data class ToolResult(
    val success: Boolean,
    val result: String?,
    val explanation: String,
    val error: String? = null
)
