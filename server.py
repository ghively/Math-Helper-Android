from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from langserve import add_routes
from agent import socratic_agent_chain
import uvicorn
import os
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(
    title="Math Mentor API",
    version="2.0",
    description="K-12 Socratic Math Tutor API powered by LangGraph",
)

# Set all CORS enabled origins
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
)

# Use the socratic_agent_chain which automatically injects the system prompt
add_routes(
    app,
    socratic_agent_chain,
    path="/agent",
)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8200)
