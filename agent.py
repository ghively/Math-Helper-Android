import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langgraph.prebuilt import create_react_agent
from langchain_core.tools import tool
import sympy as sp
import re
from typing import Optional

# Load environment variables
load_dotenv()

# =============================================================================
# SOCRATIC MATH TUTOR SYSTEM PROMPTS
# =============================================================================

SOCCRATIC_TUTOR_INSTRUCTIONS = """You are a Socratic Math Tutor for K-12 students. Your name is "Math Mentor".

## CORE PRINCIPLE: NEVER Give Direct Answers

Your job is to GUIDE students to discover answers themselves through thoughtful questions aligned with Common Core Math Practices.

## COMMON CORE MATH PRACTICES - Always incorporate these:

1. **Make sense of problems and persevere**
   - "Can you explain this problem in your own words?"
   - "What's the first thing you notice?"
   - "What information matters here?"

2. **Reason abstractly and quantitatively**
   - "What does this number represent in the story?"
   - "Can you draw a picture of what's happening?"
   - "How would this look with blocks/counter?"

3. **Construct viable arguments**
   - "Why do you think that?"
   - "Convince me your answer makes sense"
   - "How could you prove that to a classmate?"

4. **Model with mathematics**
   - "Can you represent this with a diagram/equation?"
   - "What visual model shows this?" (number lines, arrays, bar models, tape diagrams)

5. **Use appropriate tools strategically**
   - "Would a number line help here?"
   - "What tool could make this easier?"
   - "Could we use manipulatives to think through this?"

6. **Attend to precision**
   - "What exactly do those units mean?"
   - "Be precise - what does 'x' stand for here?"
   - "Check your labels and units"

7. **Look for and use structure**
   - "Do you see a pattern here?"
   - "What's the same about these problems?"
   - "How does this connect to what you already know?"

8. **Look for and express regularity**
   - "If we changed this number, what would happen?"
   - "Will this shortcut always work? Why?"

## SOCRATIC METHOD APPROACH:

1. **Start with Understanding**
   - Ask "What do you think this problem is asking?"
   - "What information do we have?"
   - "What are we trying to find?"

2. **Guide, Don't Tell**
   - Ask "What do you think should happen first?"
   - "Why did you choose that approach?"
   - "What would happen if we tried ___?"
   - Give hints, not solutions

3. **Build Confidence**
   - "Great thinking!" "You're on the right track!"
   - "That's an interesting approach - let's explore it"
   - Celebrate small wins and effort

4. **When Stuck**
   - Break into smaller steps
   - "Let's think about just the first part"
   - "What's a simpler version of this problem?"
   - Suggest visual representations

5. **Verify Understanding**
   - "Can you explain why that works?"
   - "How would you check your answer?"
   - "Teach this back to me like I'm learning it"

## GRADE LEVEL ADAPTATION:

**Elementary (K-5):**
- Use visual models heavily (arrays, number lines, bar models)
- Concrete → pictorial → abstract progression
- Emphasize understanding "why" before "how"
- Use manipulatives and real-world contexts

**Middle School (6-8):**
- Multiple solution strategies
- Connecting to prior learning
- Proportional reasoning and patterns
- Algebraic thinking foundations

**High School (9-12):**
- Modeling real-world situations
- Multiple representations (graphical, numerical, algebraic)
- Justification and proof
- Connecting concepts across domains

## TEACHING METHODS (Best Practices):

- **Conceptual first, procedural second**: Build understanding before algorithms
- **Multiple representations**: Numbers, words, pictures, equations, graphs, manipulatives
- **Student explanation**: "Tell me how you got that" more than "Here's how to do it"
- **Productive struggle**: Let students grapple - don't rescue too quickly
- **Number talks**: Mental math strategies and discussing approaches
- **Error analysis**: "What thinking might lead to this mistake?"
- **Connection making**: "How is this like what we did before?"

## EXAMPLE INTERACTIONS:

**Elementary:**
Student: "I don't know 7 × 8"
Tutor: "What's a 7 fact you DO know? Like 7 × 5?"
Student: "35"
Tutor: "Great! So how many more 7s do we need to get to 8? Can you picture it with arrays?"

**Middle School:**
Student: "I don't know how to solve 2x + 5 = 13"
Tutor: "What's the goal when we have an equation like this? Can you draw a balance scale to show it?"
Student: "To find x?"
Tutor: "Exactly! If 2x + 5 is on one side, what does it equal? How could we get x alone step by step?"

**High School:**
Student: "How do I graph y = 2x + 3?"
Tutor: "What does the 2 tell us about the line? What about the 3? Could you make a table of values first?"

## TOOLS AVAILABLE:
- calculate: Compute and verify mathematical expressions
- solve_equation: Solve algebraic equations (use privately to check student work)
- simplify: Simplify mathematical expressions

Remember: Use tools to CHECK student work, not to give answers. Focus on conceptual understanding, multiple strategies, visual models, and student explanation. The journey of discovery is more important than the answer.
"""

# =============================================================================
# MATH TOOLS
# =============================================================================

@tool
def calculate(expression: str) -> str:
    """
    Safely evaluate a mathematical expression.

    Use this to:
    - Verify a student's calculation
    - Check intermediate steps
    - Demonstrate a pattern (after student discovers it)

    Supports: +, -, *, /, **, (), sqrt(), sin(), cos(), etc.
    """
    try:
        # Safe evaluation with limited scope
        allowed_names = {
            'sqrt': sp.sqrt,
            'sin': sp.sin,
            'cos': sp.cos,
            'tan': sp.tan,
            'log': sp.log,
            'exp': sp.exp,
            'pi': sp.pi,
            'e': sp.E,
            'abs': abs,
            'round': round,
        }

        # Parse as sympy for safer evaluation
        result = sp.sympify(expression, locals=allowed_names)
        # Evaluate to numeric if possible
        numeric_result = sp.N(result)

        return f"{expression} = {numeric_result}"
    except Exception as e:
        return f"Could not calculate '{expression}': {str(e)}"

@tool
def solve_equation(equation: str, variable: str = "x") -> str:
    """
    Solve an algebraic equation for a variable.

    Use this PRIVATELY to check if student's answer is correct.
    Format: "expression = expression" or just "expression" for = 0

    Examples:
    - "x^2 - 4 = 0"
    - "2*x + 5 = 13"
    - "x + 3*y = 7, x - y = 1" (system of equations)
    """
    try:
        # Handle multiple equations (systems)
        if ',' in equation:
            eqs = [eq.strip() for eq in equation.split(',')]
            symbols = sp.symbols(variable)
            if isinstance(symbols, tuple):
                varsyms = symbols
            else:
                varsyms = (symbols,)

            sympy_eqs = []
            for eq in eqs:
                if '=' in eq:
                    left, right = eq.split('=')
                    sympy_eqs.append(sp.Eq(sp.sympify(left.strip()), sp.sympify(right.strip())))
                else:
                    sympy_eqs.append(sp.Eq(sp.sympify(eq.strip()), 0))

            solutions = sp.solve(sympy_eqs, varsyms)

            if not solutions:
                return "No solution found"

            if len(varsyms) == 1:
                return f"x = {solutions}"
            else:
                result = []
                for i, sol in enumerate(solutions):
                    result.append(f"{varsyms[i]} = {sol}")
                return ", ".join(result)

        # Single equation
        if '=' in equation:
            left, right = equation.split('=')
            sympy_eq = sp.Eq(sp.sympify(left.strip()), sp.sympify(right.strip()))
        else:
            sympy_eq = sp.Eq(sp.sympify(equation), 0)

        var = sp.symbols(variable)
        solutions = sp.solve(sympy_eq, var)

        if not solutions:
            return "No solution found"
        elif len(solutions) == 1:
            return f"{variable} = {solutions[0]}"
        else:
            return f"{variable} = {solutions}"

    except Exception as e:
        return f"Could not solve equation: {str(e)}"

@tool
def simplify_expression(expression: str) -> str:
    """
    Simplify a mathematical expression.

    Use to check if a student's simplified form is correct.
    """
    try:
        expr = sp.sympify(expression)
        simplified = sp.simplify(expr)
        return f"{expression} simplifies to: {simplified}"
    except Exception as e:
        return f"Could not simplify: {str(e)}"

@tool
def get_hint(topic: str) -> str:
    """
    Get a teaching hint for a math topic.

    Use this when you need ideas for how to guide a student
    without giving away the answer.

    Topics: algebra, geometry, fractions, word-problems, etc.
    """
    hints = {
        "algebra": "Ask: 'What's the unknown?' 'What represents what?' Encourage using variables for unknowns.",
        "geometry": "Ask: 'Can you draw this?' 'What shapes do you see?' Visual thinking helps.",
        "fractions": "Ask: 'What does the denominator tell us?' 'What about the numerator?' Use visual models.",
        "word-problems": "Ask: 'What's the story here?' 'What are we actually trying to find?' Identify the question first.",
        "equations": "Ask: 'What's the goal?' 'How can we isolate the variable?' Think about inverse operations.",
    }
    return hints.get(topic.lower(), "Ask: 'What do you understand about this problem?' Start from what they know.")

# =============================================================================
# AGENT SETUP
# =============================================================================

def get_agent():
    api_key = os.getenv("OPENAI_API_KEY")
    base_url = os.getenv("OPENAI_BASE_URL")

    if not api_key:
        print("Error: OPENAI_API_KEY not found in .env file")
        return None

    # Initialize LLM with custom endpoint
    llm_kwargs = {"temperature": 0.7}
    if base_url:
        llm_kwargs["base_url"] = base_url

    llm = ChatOpenAI(
        model="glm-4.5-air",  # Faster model for real-time tutoring
        **llm_kwargs
    )

    # Math tools
    tools = [calculate, solve_equation, simplify_expression, get_hint]

    # Create ReAct agent with the LLM
    graph = create_react_agent(llm, tools=tools)

    return graph

# Base graph for LangGraph Studio
compiled_graph = get_agent()

# =============================================================================
# WRAPPED CHAIN FOR WEB UI (injects Socratic prompt)
# =============================================================================

from langchain_core.runnables import RunnableLambda
from langchain_core.messages import SystemMessage

def _inject_socratic_prompt(state):
    """Inject the Socratic system prompt before user messages"""
    messages = state.get("messages", [])

    # Check if the first message is already our system prompt
    # (to avoid double-injection in multi-turn conversations)
    if messages and isinstance(messages[0], SystemMessage):
        # Check if it's our Socratic prompt (starts with our identifier)
        if "Socratic Math Tutor" in messages[0].content:
            return state

    # Prepend the Socratic system prompt
    return {"messages": [SystemMessage(content=SOCCRATIC_TUTOR_INSTRUCTIONS)] + messages}

# Create a wrapped chain that injects the system prompt
# This is what the web UI server will use
socratic_agent_chain = (
    RunnableLambda(_inject_socratic_prompt) | compiled_graph
)
