#!/usr/bin/env python3
"""
Helper Chat & Trajectory Exporter
Exports the conversation history, user requests, model responses, and actions to a Markdown document.
"""

import json
import os
import sys
from datetime import datetime

TRANSCRIPT_PATH = r"C:\Users\JMD\.gemini\antigravity\brain\b641ff4a-2833-4782-8a6d-20d82b4ceb25\.system_generated\logs\transcript_full.jsonl"
OUTPUT_MD_PATH = r"D:\GPU-D\apps\Helper\CONVERSATION_HISTORY.md"

def export_transcript():
    if not os.path.exists(TRANSCRIPT_PATH):
        print(f"Error: {TRANSCRIPT_PATH} not found")
        return

    print("Reading transcript...")
    steps = []
    with open(TRANSCRIPT_PATH, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                steps.append(json.loads(line))
            except Exception:
                continue

    print(f"Loaded {len(steps)} steps.")

    # Group into Turns based on USER_INPUT
    turns = []
    current_turn = None

    for step in steps:
        stype = step.get("type")
        source = step.get("source")
        created_at = step.get("created_at", "")
        content = step.get("content", "")
        tool_calls = step.get("tool_calls", [])

        if stype == "USER_INPUT" and source == "USER_EXPLICIT":
            if current_turn is not None:
                turns.append(current_turn)
            current_turn = {
                "user_prompt": content,
                "timestamp": created_at,
                "model_responses": [],
                "tool_actions": [],
                "artifacts": []
            }
        else:
            if current_turn is None:
                current_turn = {
                    "user_prompt": "Initial Task Context / Auto-Start",
                    "timestamp": created_at,
                    "model_responses": [],
                    "tool_actions": [],
                    "artifacts": []
                }

            # Record tool calls
            for tc in tool_calls:
                name = tc.get("name", "")
                args = tc.get("args", {})
                current_turn["tool_actions"].append({
                    "name": name,
                    "args": args,
                    "timestamp": created_at
                })
                if name in ("write_file", "edit_file"):
                    p = args.get("Path", "")
                    if "brain" in p and p.endswith(".md"):
                        current_turn["artifacts"].append(p)

            # Record model assistant messages to user
            if source == "MODEL" and stype == "PLANNER_RESPONSE" and content and not tool_calls:
                current_turn["model_responses"].append(content)

    if current_turn is not None:
        turns.append(current_turn)

    print(f"Organized into {len(turns)} turns. Writing Markdown...")

    with open(OUTPUT_MD_PATH, "w", encoding="utf-8") as out:
        out.write("# Helper — Complete Conversation & Execution History\n\n")
        out.write(f"> **Last Updated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        out.write(f"> **Conversation ID:** `b641ff4a-2833-4782-8a6d-20d82b4ceb25`\n")
        out.write(f"> **Total Steps Processed:** {len(steps)}\n")
        out.write(f"> **Total User Interaction Turns:** {len(turns)}\n\n")
        out.write("---\n\n")

        for idx, turn in enumerate(turns, 1):
            out.write(f"## Turn {idx}\n\n")
            out.write(f"**Timestamp:** `{turn['timestamp']}`\n\n")
            out.write("### 👤 User Request\n\n")
            out.write("```markdown\n" + turn['user_prompt'].strip() + "\n```\n\n")

            if turn["tool_actions"]:
                out.write("### 🛠️ Key Actions & Tools Executed\n\n")
                action_counts = {}
                for a in turn["tool_actions"]:
                    name = a["name"]
                    action_counts[name] = action_counts.get(name, 0) + 1
                
                summary_str = ", ".join([f"`{k}` ({v})" for k, v in sorted(action_counts.items())])
                out.write(f"**Total Tool Calls:** {len(turn['tool_actions'])} — {summary_str}\n\n")
                
                out.write("<details>\n<summary>Click to view tool execution trace (sample up to 25 calls)</summary>\n\n")
                for a in turn["tool_actions"][:25]:
                    name = a["name"]
                    args = a.get("args", {})
                    if name == "run_command":
                        cmd = args.get("CommandLine", "").strip()
                        if len(cmd) > 100:
                            cmd = cmd[:97] + "..."
                        summary = args.get("toolSummary", "")
                        out.write(f"- ⚡ `run_command` ({summary}): `{cmd}`\n")
                    elif name in ("write_file", "edit_file"):
                        path = args.get("Path", "")
                        out.write(f"- 📝 `{name}`: `{os.path.basename(path)}`\n")
                    elif name == "read_file":
                        path = args.get("Path", "")
                        out.write(f"- 📖 `read_file`: `{os.path.basename(path)}`\n")
                    else:
                        out.write(f"- 🔧 `{name}`\n")
                if len(turn["tool_actions"]) > 25:
                    out.write(f"- *...and {len(turn['tool_actions']) - 25} additional operations*\n")
                out.write("\n</details>\n\n")

            if turn["artifacts"]:
                out.write("### 📄 Artifacts Created / Updated\n\n")
                unique_artifacts = list(dict.fromkeys(turn["artifacts"]))
                for art in unique_artifacts:
                    out.write(f"- [`{os.path.basename(art)}`]({art})\n")
                out.write("\n")

            if turn["model_responses"]:
                out.write("### 🤖 Assistant Responses\n\n")
                for resp in turn["model_responses"]:
                    out.write(resp.strip() + "\n\n")

            out.write("---\n\n")

    print(f"Successfully generated: {OUTPUT_MD_PATH}")

if __name__ == "__main__":
    export_transcript()
