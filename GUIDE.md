# Visualization and Learning Guide

This project is set up to help you learn LangChain and LangGraph. Here are the tools available to you.

## 1. Visualizing the Graph

### Quick ASCII View
When running the main agent loop (`python main.py`), you can type `graph` to see a text-based representation of the agent's workflow.

### Generate Diagrams
Run the included tool to generate visual diagrams of your agent:

```bash
python tools/visualize_graph.py
```

This will:
1. Print an ASCII representation.
2. Attempt to save `agent_graph.png`.
3. Print Mermaid.js code that you can copy into [Mermaid Live Editor](https://mermaid.live).

## 2. Using LangGraph Studio

LangGraph Studio is powerful local IDE for your agents.

1. Ensure you have `langgraph-cli` installed (included in requirements).
2. Run the development server:
   ```bash
   langgraph dev
   ```
   (Note: You might need to install Docker Desktop for this to work fully in some environments, though the CLI is installed).

## 3. LangSmith (Observability)

To see exactly what is happening inside your agent (inputs, outputs, latency, prompts):

1. Sign up at [smith.langchain.com](https://smith.langchain.com/).
2. Create an API Key.
3. Add these lines to your `.env` file:
   ```bash
   LANGCHAIN_TRACING_V2=true
   LANGCHAIN_API_KEY=lsv2_...
   ```
4. Run your agent again. You will see traces appear in the LangSmith UI.
