package com.mathagent

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ReAct Agent for Socratic Math Tutoring
 *
 * Implements the ReAct (Reasoning + Acting) pattern:
 * Thought → Action → Observation → Thought → ... → Final Answer
 *
 * Uses GBNF grammar to force structured JSON output from the LLM,
 * preventing hallucinated tool calls and ensuring valid ReAct loop.
 *
 * Based on the system prompt from langchain-math/agent.py:
 * - Socratic method (teaching by questioning, not giving answers)
 * - Common Core Math Practices alignment
 * - Tools: calculate, solve_equation, simplify_expression, expand_expression,
 *           factor_expression, get_hint, verify_worked_example, check_answer
 */
class ReActAgent(
    private val llamaEngine: LlamaEngine,
    private val context: Context
) {
    // Math tools instance with context
    private val mathTools = MathTools(context)

    // System prompt for Socratic Math Tutor
    private val systemPrompt = """
        You are a Socratic Math Tutor aligned with Common Core Math Practices.

        ## Your Role
        Guide students to discover mathematical answers through questioning,
        not by providing solutions directly.

        ## Teaching Approach (Socratic Method)
        - Ask thought-provoking questions that lead to insight
        - Provide hints that advance understanding without giving the answer
        - Celebrate productive mistakes as learning opportunities
        - Build confidence through incremental challenges

        ## Common Core Alignment
        MP1: Make sense of problems and persevere
        MP2: Reason abstractly and quantitatively
        MP3: Construct viable arguments and critique reasoning
        MP4: Model with mathematics
        MP5: Use appropriate tools strategically
        MP6: Attend to precision
        MP7: Look for and make use of structure
        MP8: Look for and express regularity in repeated reasoning

        ## Tools Available

        ### Calculation Tools
        - calculate: Evaluate numeric expressions (e.g., "2 + 2", "3.5 * 4")

        ### Algebra Tools
        - solve_equation: Solve equations (e.g., "2x + 5 = 15", "x^2 - 4 = 0")
        - simplify_expression: Simplify algebraic expressions (e.g., "2x + 3x", "(x+1)^2")
        - expand_expression: Expand expressions (e.g., "(x+1)^2", "2(x+3)")
        - factor_expression: Factor expressions (e.g., "x^2 - 4", "2x + 4")

        ### Teaching Tools
        - get_hint: Generate a pedagogical hint for a problem
        - verify_worked_example: Check student's step-by-step work
        - check_answer: Verify if a student's answer is correct

        ## Response Format
        Think step by step. Use tools when needed. Always explain your reasoning.

        When you need to use a tool, output:
        {"action": "tool_name", "input": "parameter"}

        When you have the final answer, output:
        {"answer": "your response"}

        Remember: Your goal is to guide the student to understanding,
        not to do the work for them.
    """.trimIndent()

    /**
     * Process a user message through the ReAct loop
     *
     * Streams responses as tokens are generated
     */
    fun chat(userMessage: String): Flow<AgentEvent> = flow {
        // Build prompt with system instructions
        val fullPrompt = buildFullPrompt(userMessage)

        // ReAct loop: Thought → Action → Observation → ...
        var remainingIterations = MAX_ITERATIONS
        var finalAnswer: String? = null
        var conversationHistory = fullPrompt

        while (remainingIterations-- > 0 && finalAnswer == null) {
            // Generate from LLM with grammar-constrained output
            val response = StringBuilder()
            llamaEngine.generate(
                prompt = conversationHistory,
                grammar = REACT_JSON_GRAMMAR,
                onToken = { token ->
                    response.append(token)
                    emit(AgentEvent.Token(token))
                }
            )

            val responseText = response.toString().trim()

            // Parse the JSON response
            when {
                responseText.contains("\"action\"") -> {
                    // Tool call detected
                    val toolCall = parseToolCall(responseText)

                    // Execute the tool
                    emit(AgentEvent.ToolCall(toolCall.action, toolCall.input))
                    val result = executeTool(toolCall.action, toolCall.input, userMessage)

                    // Feed observation back to LLM
                    val observation = if (result.success) {
                        "Tool ${toolCall.action} returned: ${result.result}\n${result.explanation}"
                    } else {
                        "Tool ${toolCall.action} failed: ${result.error}"
                    }

                    emit(AgentEvent.ToolResult(toolCall.action, result.success, observation))

                    // Add to conversation for next iteration
                    conversationHistory += "\nObservation: $observation\n"
                }

                responseText.contains("\"answer\"") -> {
                    // Final answer
                    finalAnswer = extractAnswer(responseText)
                    emit(AgentEvent.FinalAnswer(finalAnswer))
                }

                else -> {
                    // Regular text response (no tool call)
                    emit(AgentEvent.FinalAnswer(responseText))
                    break
                }
            }
        }

        if (finalAnswer == null && maxIterations <= 0) {
            emit(AgentEvent.Error("Agent exceeded maximum iterations"))
        }
    }

    // ==========================================================================
    // Tool implementations (suspend for async operations)
    // ==========================================================================

    private suspend fun executeTool(toolName: String, input: String, context: String): ToolResult {
        val cleanInput = input.removeSurrounding("\"")

        return when (toolName) {
            "calculate" -> mathTools.calculate(cleanInput)
            "solve_equation" -> mathTools.solveEquation(cleanInput)
            "simplify_expression" -> mathTools.simplifyExpression(cleanInput)
            "expand_expression" -> mathTools.expandExpression(cleanInput)
            "factor_expression" -> mathTools.factorExpression(cleanInput)
            "get_hint" -> mathTools.getHint(cleanInput)
            "verify_worked_example" -> {
                // Input format: "problem: <problem>, work: <work>"
                val parts = cleanInput.split(", work: ")
                val problem = parts.getOrNull(0)?.removePrefix("problem: ") ?: ""
                val work = parts.getOrNull(1) ?: ""
                mathTools.verifyWorkedExample(problem, work)
            }
            "check_answer" -> {
                // Input format: "problem: <problem>, answer: <answer>"
                val parts = cleanInput.split(", answer: ")
                val problem = parts.getOrNull(0)?.removePrefix("problem: ") ?: ""
                val answer = parts.getOrNull(1) ?: ""
                mathTools.checkAnswer(problem, answer)
            }
            else -> ToolResult(
                success = false,
                result = null,
                explanation = "Unknown tool: $toolName",
                error = "Tool not found"
            )
        }
    }

    // ==========================================================================
    // Prompt building and parsing
    // ==========================================================================

    private fun buildFullPrompt(userMessage: String): String {
        return """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$userMessage<|im_end|>
<|im_start|>assistant
"""
    }

    private fun parseToolCall(response: String): ToolCall {
        // Extract action and input from JSON like: {"action": "calculate", "input": "2+2"}
        val actionMatch = ACTION_PATTERN.find(response)
        val inputMatch = INPUT_PATTERN.find(response)

        return ToolCall(
            thought = extractThought(response) ?: "Thinking...",
            action = actionMatch?.groupValues?.get(1) ?: "",
            input = inputMatch?.groupValues?.get(1) ?: ""
        )
    }

    private fun extractThought(response: String): String? {
        // Look for thought field if present
        return THOUGHT_PATTERN.find(response)?.groupValues?.get(1)
    }

    private fun extractAnswer(response: String): String {
        // Extract answer from JSON like: {"answer": "The result is 4"}
        return ANSWER_PATTERN.find(response)?.groupValues?.get(1) ?: response
    }

    // ==========================================================================
    // Companion object - constants and compiled patterns
    // ==========================================================================

    companion object {
        // Maximum number of ReAct iterations before giving up
        private const val MAX_ITERATIONS = 10

        // Pre-compiled regex patterns for JSON parsing
        private val ACTION_PATTERN = Regex(""""action":\s*"([^"]+)"""")
        private val INPUT_PATTERN = Regex(""""input":\s*"([^"]+)"""")
        private val THOUGHT_PATTERN = Regex(""""thought":\s*"([^"]+)"""")
        private val ANSWER_PATTERN = Regex(""""answer":\s*"([^"]+)"""")

        /**
         * Optimized GBNF Grammar for llama.cpp
         *
         * Forces the LLM to output valid JSON with either:
         * - Tool call: {"action": "...", "input": "..."}
         * - Final answer: {"answer": "..."}
         *
         * Optimizations:
         * - Uses escape sequences for quotes in strings
         * - Allows nested JSON with escaped content
         * - More robust pattern matching
         */
        const val REACT_JSON_GRAMMAR = """
            root ::= tool_call | final_answer | text

            tool_call ::= "{" ws "action" ws ":" ws quote action quote "," ws "input" ws ":" ws quote input quote "}"
            final_answer ::= "{" ws "answer" ws ":" ws quote text quote "}"
            text ::= [^\n"]*

            action ::= "calculate" | "solve_equation" | "simplify_expression" | "expand_expression" | "factor_expression" | "get_hint" | "verify_worked_example" | "check_answer"
            input ::= string_content
            string_content ::= ([^"\\] | escape)*
            escape ::= "\\" (["\\nrt/])
            text ::= [^"\n]*

            ws ::= " "*
            quote ::= '"'
        """
    }

    private data class ToolCall(
        val thought: String,
        val action: String,
        val input: String
    )
}

/**
 * Events emitted during ReAct loop execution
 */
sealed class AgentEvent {
    /** Individual token being streamed */
    data class Token(val text: String) : AgentEvent()

    /** Tool being called */
    data class ToolCall(val tool: String, val input: String) : AgentEvent()

    /** Result from tool execution */
    data class ToolResult(val tool: String, val success: Boolean, val output: String) : AgentEvent()

    /** Final answer from agent */
    data class FinalAnswer(val text: String) : AgentEvent()

    /** Error occurred */
    data class Error(val message: String) : AgentEvent()
}
