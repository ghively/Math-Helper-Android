# Math Mentor - Socratic K-12 Math Tutor

An AI-powered math tutor that uses the **Socratic method** to help students discover answers through thoughtful questions rather than giving solutions.

## Features

- **Socratic Learning** - Guides students to discover answers themselves
- **K-12 Coverage** - Adapts to elementary, middle school, and high school levels
- **Math Tools**
  - Calculate expressions safely
  - Solve algebraic equations
  - Simplify expressions
  - Get topic-specific hints

## Getting Started

### 1. Install Dependencies

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Configure API

Edit `.env` and add your Z.ai API credentials:

```bash
OPENAI_API_KEY=your_api_key_here
OPENAI_BASE_URL=https://api.z.ai/api/coding/paas/v4
```

### 3. Run the CLI

```bash
python main.py
```

### 4. Run the Web UI (Optional)

**Start the backend:**
```bash
python server.py
```

**Start the frontend:**
```bash
cd client
npm install
npm run dev
```

Open http://localhost:5173

## Example Interactions

```
You: I need help solving 2x + 5 = 13

Math Mentor: Hi there! I'd be happy to help you with this equation!
Let's start with the basics: What do you think the goal is when we
have an equation like 2x + 5 = 13?

You: To find x?

Math Mentor: Exactly! And what's happening to x on the left side?
```

## Project Structure

- `agent.py` - Socratic tutor agent with math tools
- `main.py` - CLI interface
- `server.py` - FastAPI server for web UI
- `client/` - React frontend

## Philosophy

> **"The journey of discovery is more important than the answer."**

Math Mentor never gives direct answers. Instead, it:
1. Asks what the student understands
2. Guides with thoughtful questions
3. Celebrates progress and effort
4. Helps verify understanding

Built with LangGraph, LangChain, and GLM-4.7.
