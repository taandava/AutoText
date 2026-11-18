#!/usr/bin/env python3
"""
Super Chat & Trajectory Exporter
Exports the conversation history, user requests, model responses, and actions to a Markdown document.
Supports dynamic conversation discovery, custom output paths, and structured turn categorization.
"""

import argparse
import glob
import json
import os
import sys
from datetime import datetime

DEFAULT_BRAIN_ROOT = r"C:\Users\JMD\.gemini\antigravity\brain"
DEFAULT_CONVERSATION_ID = "c6f82cb6-9155-422a-9715-3da090d70384"
DEFAULT_OUTPUT_MD = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "CHAT_HISTORY.md")


def find_latest_transcript(brain_root: str, conversation_id: str = None) -> tuple[str, str]:
    """Finds the transcript path and conversation ID."""
    if conversation_id:
        target = os.path.join(brain_root, conversation_id, ".system_generated", "logs", "transcript_full.jsonl")
        if os.path.exists(target):
            return target, conversation_id
        # Fallback to compact transcript
        target_compact = os.path.join(brain_root, conversation_id, ".system_generated", "logs", "transcript.jsonl")
        if os.path.exists(target_compact):
            return target_compact, conversation_id

    # If conversation_id not found or not provided, search for newest modified transcript
    pattern = os.path.join(brain_root, "*", ".system_generated", "logs", "transcript_full.jsonl")
    matches = glob.glob(pattern)
    if not matches:
        pattern_compact = os.path.join(brain_root, "*", ".system_generated", "logs", "transcript.jsonl")
        matches = glob.glob(pattern_compact)

    if not matches:
        return "", ""

    # Sort by modification time descending
    matches.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    best = matches[0]
    parts = best.split(os.sep)
    # Extract conversation UUID
    try:
        conv_idx = parts.index("brain") + 1
        cid = parts[conv_idx]
    except Exception:
        cid = "unknown"
    return best, cid


def export_transcript(transcript_path: str, conversation_id: str, output_path: str):
    if not os.path.exists(transcript_path):
        print(f"Error: Transcript path '{transcript_path}' does not exist.")
        return False

    print(f"Reading transcript from: {transcript_path}")
    steps = []
    with open(transcript_path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                steps.append(json.loads(line))
            except Exception:
                continue

    print(f"Loaded {len(steps)} steps.")

    # Group into turns based on USER_INPUT
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

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as out:
        out.write("# Super — Complete Conversation & Execution History\n\n")
        out.write(f"> **Application:** Super (`com.jmd.superapp`)\n")
        out.write(f"> **Last Synchronized:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        out.write(f"> **Conversation ID:** `{conversation_id}`\n")
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

    print(f"Successfully generated: {output_path}")
    return True


def main():
    parser = argparse.ArgumentParser(description="Super Chat & Trajectory Exporter")
    parser.add_argument("--conversation-id", default=DEFAULT_CONVERSATION_ID, help="Conversation UUID")
    parser.add_argument("--transcript", default=None, help="Explicit path to transcript_full.jsonl")
    parser.add_argument("--output", default=DEFAULT_OUTPUT_MD, help="Output markdown path")
    parser.add_argument("--brain-root", default=DEFAULT_BRAIN_ROOT, help="Brain root directory")
    args = parser.parse_args()

    transcript_path = args.transcript
    cid = args.conversation_id
    if not transcript_path:
        transcript_path, cid = find_latest_transcript(args.brain_root, args.conversation_id)

    if not transcript_path:
        print(f"Error: Could not find any transcript file in {args.brain_root}")
        sys.exit(1)

    success = export_transcript(transcript_path, cid, args.output)
    if not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
