from agent import compiled_graph, SOCCRATIC_TUTOR_INSTRUCTIONS
from langchain_core.messages import SystemMessage, HumanMessage

def main():
    if compiled_graph is None:
        return

    print("=" * 60)
    print("  🎓 SOCRATIC MATH TUTOR - K-12")
    print("=" * 60)
    print("\nHello! I'm your Math Mentor.")
    print("I'll help you discover answers through questions,")
    print("not just give you solutions. That's how we learn!\n")
    print("Try asking me:")
    print("  • 'Help me solve 2x + 5 = 13'")
    print("  • 'I don't understand fractions'")
    print("  • 'Can you help me with this word problem...'")
    print("\nType 'graph' to see how I think, or 'exit' to quit.")
    print("=" * 60)
    print()

    while True:
        try:
            user_input = input("You: ")
            if user_input.lower() in ["exit", "quit"]:
                print("\nKeep practicing! You've got this! 💪")
                break

            if user_input.lower() == "graph":
                print(compiled_graph.get_graph().draw_ascii())
                continue

            # Prepend system message for every request to ensure Socratic behavior
            messages = [
                SystemMessage(content=SOCCRATIC_TUTOR_INSTRUCTIONS),
                HumanMessage(content=user_input)
            ]

            response = compiled_graph.invoke({"messages": messages})

            if "messages" in response and len(response["messages"]) > 0:
                print(f"\nMath Mentor: {response['messages'][-1].content}\n")

        except KeyboardInterrupt:
            print("\n\nKeep practicing! You've got this! 💪")
            break
        except Exception as e:
            print(f"\nOops! Something went wrong: {e}\n")

if __name__ == "__main__":
    main()
