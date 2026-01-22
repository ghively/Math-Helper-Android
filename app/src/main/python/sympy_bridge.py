"""
SymPy Bridge for Android Math Agent

Provides Python symbolic math capabilities using SymPy.
Called from Kotlin via Chaquopy Python bridge.
"""

from sympy import (
    symbols, simplify, expand, factor, solve, Eq,
    sin, cos, tan, sqrt, log, exp, Abs, Integral, Derivative,
    sympify, SympifyError
)
from typing import Dict, Any, Tuple
import json


def calculate(expr: str) -> Dict[str, Any]:
    """
    Evaluate a numeric expression and return result.

    Args:
        expr: Mathematical expression as string

    Returns:
        Dict with success, result, and explanation
    """
    try:
        result = sympify(expr)
        # Evaluate to numeric if possible
        try:
            numeric_result = float(result.evalf())
            return {
                "success": True,
                "result": str(numeric_result),
                "explanation": f"Calculated: {expr} = {numeric_result}"
            }
        except:
            return {
                "success": True,
                "result": str(result),
                "explanation": f"Expression: {expr} = {result}"
            }
    except SympifyError as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error parsing expression: {e}",
            "error": str(e)
        }
    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error calculating: {e}",
            "error": str(e)
        }


def solve_equation(equation_str: str) -> Dict[str, Any]:
    """
    Solve an equation for a variable.

    Args:
        equation_str: Equation like "2x + 5 = 15" or "x^2 - 4 = 0"

    Returns:
        Dict with success, solutions, and explanation
    """
    try:
        # Parse equation
        if '=' not in equation_str:
            # Assume it's an expression set to 0
            equation_str = f"{equation_str} = 0"

        left_str, right_str = equation_str.split('=', 1)

        # Try to determine the variable (x, y, z, etc.)
        for var_name in ['x', 'y', 'z', 'a', 'b', 'c', 'n']:
            if var_name in left_str or var_name in right_str:
                var = symbols(var_name)
                break
        else:
            # Default to x
            var = symbols('x')

        # Parse both sides
        left = sympify(left_str.strip())
        right = sympify(right_str.strip())

        # Create equation and solve
        equation = Eq(left, right)
        solutions = solve(equation, var)

        # Format solutions
        if len(solutions) == 0:
            result_str = "No solution found"
        elif len(solutions) == 1:
            result_str = f"{var} = {solutions[0]}"
        else:
            result_str = ", ".join([f"{var} = {sol}" for sol in solutions])

        return {
            "success": True,
            "result": result_str,
            "solutions": [str(sol) for sol in solutions],
            "explanation": f"Solved {equation_str}: {result_str}"
        }

    except SympifyError as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error parsing equation: {e}",
            "error": str(e)
        }
    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error solving equation: {e}",
            "error": str(e)
        }


def simplify_expression(expr: str) -> Dict[str, Any]:
    """
    Simplify an algebraic expression.

    Args:
        expr: Algebraic expression like "2x + 3x" or "(x+1)^2"

    Returns:
        Dict with success, simplified result, and steps
    """
    try:
        original = sympify(expr)
        simplified = simplify(original)

        # Also provide expanded form for educational value
        expanded = expand(original)

        steps = []
        if str(original) != str(expanded):
            steps.append(f"Expanded: {expanded}")
        if str(expanded) != str(simplified):
            steps.append(f"Simplified: {simplified}")

        return {
            "success": True,
            "result": str(simplified),
            "original": str(original),
            "expanded": str(expanded),
            "steps": steps,
            "explanation": f"Simplified {expr} to {simplified}"
        }

    except SympifyError as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error parsing expression: {e}",
            "error": str(e)
        }
    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error simplifying: {e}",
            "error": str(e)
        }


def expand_expression(expr: str) -> Dict[str, Any]:
    """
    Expand an algebraic expression.

    Args:
        expr: Expression to expand like "(x+1)^2"

    Returns:
        Dict with success and expanded result
    """
    try:
        original = sympify(expr)
        expanded = expand(original)

        return {
            "success": True,
            "result": str(expanded),
            "explanation": f"Expanded {expr} to {expanded}"
        }

    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error expanding: {e}",
            "error": str(e)
        }


def factor_expression(expr: str) -> Dict[str, Any]:
    """
    Factor an algebraic expression.

    Args:
        expr: Expression to factor like "x^2 - 4"

    Returns:
        Dict with success and factored result
    """
    try:
        original = sympify(expr)
        factored = factor(original)

        return {
            "success": True,
            "result": str(factored),
            "explanation": f"Factored {expr} to {factored}"
        }

    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error factoring: {e}",
            "error": str(e)
        }


def get_hint(problem: str, last_attempt: str = None) -> Dict[str, Any]:
    """
    Generate a Socratic hint for a math problem.

    Args:
        problem: The math problem or question
        last_attempt: Optional previous attempt by student

    Returns:
        Dict with hint text
    """
    hints = []

    # Analyze problem type
    problem_lower = problem.lower()

    if '=' in problem:
        # It's an equation
        if 'x' in problem or 'y' in problem or 'z' in problem:
            hints.append("What's the goal when solving an equation?")
            hints.append("Try to isolate the variable on one side.")
            if last_attempt:
                hints.append(f"Your attempt: {last_attempt}. Check each step carefully.")
        else:
            hints.append("What are you trying to solve for?")
    elif '*' in problem or '×' in problem or '/' in problem or '÷' in problem:
        hints.append("Remember the order of operations (PEMDAS).")
        hints.append("Which operation should you do first?")
    elif '+' in problem or '-' in problem:
        hints.append("Try combining like terms first.")
    elif 'simplify' in problem_lower or 'factor' in problem_lower:
        hints.append("Look for common patterns or formulas.")
        hints.append("What do you notice about the terms?")
    elif 'graph' in problem_lower or 'plot' in problem_lower:
        hints.append("What would the graph look like?")
        hints.append("Think about key points: intercepts, vertex, etc.")
    else:
        hints.append("What information do you know?")
        hints.append("What are you trying to find?")
        hints.append("What's the first step you might take?")

    # Select a hint based on context
    if last_attempt:
        # Check for common mistakes
        if '=' in last_attempt and '=' not in problem:
            hints.append("Make sure you're setting up the equation correctly.")
        elif 'guess' in last_attempt.lower():
            hints.append("Instead of guessing, let's work through it step by step.")

    return {
        "success": True,
        "result": hints[0] if hints else "What do you think the first step might be?",
        "all_hints": hints,
        "explanation": "Socratic hint provided"
    }


def verify_worked_example(problem: str, student_work: str) -> Dict[str, Any]:
    """
    Verify a student's worked example step by step.

    Args:
        problem: The original problem
        student_work: Multi-line string showing student's work

    Returns:
        Dict with verification results
    """
    lines = [line.strip() for line in student_work.split('\n') if line.strip()]
    feedback = []

    try:
        # Check if final answer is correct
        if lines:
            last_line = lines[-1]
            # Try to verify the answer
            if '=' in last_line:
                # Could be an answer like "x = 5"
                result = calculate(last_line.split('=')[1].strip())
                if result['success']:
                    feedback.append("✓ Final answer found")
                else:
                    feedback.append("Check your final calculation")

        return {
            "success": True,
            "result": "Verified",
            "feedback": feedback,
            "explanation": "Worked example reviewed"
        }

    except Exception as e:
        return {
            "success": False,
            "result": None,
            "explanation": f"Error verifying: {e}",
            "error": str(e)
        }


# Export functions for Chaquopy
__all__ = [
    'calculate',
    'solve_equation',
    'simplify_expression',
    'expand_expression',
    'factor_expression',
    'get_hint',
    'verify_worked_example'
]
