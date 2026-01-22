# Web Frontend for LangGraph Agent

This is a modern, premium-designed web interface for your LangChain agent.

## Tech Stack
- **Framework**: React + Vite
- **Styling**: Tailwind CSS + Glassmorphism
- **Icons**: Lucide React
- **Animations**: Framer Motion
- **API**: LangServe (Python backend)

## Setup

1. **Install Dependencies**:
   ```bash
   npm install
   ```

2. **Run Development Server**:
   ```bash
   npm run dev
   ```

3. **Open in Browser**:
   Visit the URL shown in the terminal (usually `http://localhost:5173` or `5174`).

## Connection to Backend
This frontend expects the LangServe backend to be running at `http://localhost:8100`.
Ensure you have started the backend using `python server.py` in the root `langchain-agent` directory.
