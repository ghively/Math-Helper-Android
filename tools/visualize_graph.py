import os
import sys
from agent import compiled_graph

def generate_visualization():
    if compiled_graph is None:
        print("Could not load agent.")
        return

    print("Generating ASCII representation...")
    try:
        print(compiled_graph.get_graph().draw_ascii())
    except Exception as e:
        print(f"Error drawing ascii: {e}")

    # Note: PNG generation requires graphviz which may not be installed on the system.
    # We will try it but catch errors.
    print("\nAttempting to generate Mermaid PNG...")
    try:
        png_data = compiled_graph.get_graph().draw_mermaid_png()
        output_file = "agent_graph.png"
        with open(output_file, "wb") as f:
            f.write(png_data)
        print(f"Graph saved to {output_file}")
    except Exception as e:
        print(f"Could not generate PNG (likely missing system dependencies or mermaid API access): {e}")
        print("You can copy the mermaid code below and paste it into https://mermaid.live/")
        print("-" * 20)
        print(compiled_graph.get_graph().draw_mermaid())
        print("-" * 20)

if __name__ == "__main__":
    generate_visualization()
