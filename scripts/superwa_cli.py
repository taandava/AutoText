#!/usr/bin/env python3
"""
SuperWA CLI Endpoint Tool
Provides direct CLI control over SuperWA via ADB broadcasts.
Usage:
    python scripts/superwa_cli.py status
    python scripts/superwa_cli.py send-wa --phone "+919876543210" --message "Test WA" [--business]
    python scripts/superwa_cli.py call-wa --phone "+919876543210" [--business] [--duration 15]
    python scripts/superwa_cli.py post-status --message "Hello Status" [--media "file:///..."]
    python scripts/superwa_cli.py harvest-groups [--business]
    python scripts/superwa_cli.py dump-groups
    python scripts/superwa_cli.py purge-corrupted-groups
    python scripts/superwa_cli.py banking-shield --package "com.sbi.upi" [--disable]
    python scripts/superwa_cli.py run-task --id 123
"""

import argparse
import subprocess
import sys

DEVICE_ID = "R9ZW105MFDR"

def run_adb(cmd_args):
    full_cmd = ["adb", "-s", DEVICE_ID] + cmd_args
    res = subprocess.run(full_cmd, capture_output=True, text=True)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def cmd_status(args):
    print(f"[*] Querying SuperWA Status on {DEVICE_ID}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_GET_STATUS",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver"
    ])
    print(out)
    if err:
        print(f"[Error]: {err}", file=sys.stderr)

def cmd_send_wa(args):
    print(f"[*] Dispatching WhatsApp message to {args.phone} (isBusiness={args.business})...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_SEND_WHATSAPP",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--es", "message", args.message,
        "--ez", "is_business", str(args.business).lower()
    ])
    print(out)

def cmd_call_wa(args):
    print(f"[*] Initiating WhatsApp voice call to {args.phone} (duration={args.duration}s)...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_WHATSAPP_CALL",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--ez", "is_business", str(args.business).lower(),
        "--ei", "duration", str(args.duration),
        "--ez", "include_dial_time", str(args.include_dial_time).lower()
    ])
    print(out)

def cmd_post_status(args):
    print(f"[*] Publishing WhatsApp Status story...")
    cmd = [
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_POST_STATUS",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--es", "message", args.message
    ]
    if args.media:
        cmd += ["--es", "media_uri", args.media, "--ez", "is_media", "true"]
    out, err, code = run_adb(cmd)
    print(out)

def cmd_harvest(args):
    pkg = "com.whatsapp.w4b" if args.business else "com.whatsapp"
    print(f"[*] Triggering Group Harvest on {pkg}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_HARVEST_WHATSAPP",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--es", "package", pkg
    ])
    print(out)

def cmd_dump_groups(args):
    print(f"[*] Dumping harvested groups from SuperWA repository...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_DUMP_GROUPS",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver"
    ])
    print(out)

def cmd_purge_groups(args):
    print(f"[*] Purging non-group chat entries from harvest database...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_PURGE_CORRUPTED_GROUPS",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver"
    ])
    print(out)

def cmd_shield(args):
    enable_flag = not args.disable
    action_str = "Enabling" if enable_flag else "Disabling"
    print(f"[*] {action_str} Banking Shield for {args.package}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_SET_BANKING_SHIELD",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--es", "package", args.package,
        "--ez", "enabled", str(enable_flag).lower()
    ])
    print(out)

def cmd_run_task(args):
    print(f"[*] Triggering Task #{args.id} in SuperWA...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.superwa.ACTION_DISPATCH_TASK",
        "-n", "com.jmd.superwa/.receiver.TaskDispatchReceiver",
        "--el", "task_id", str(args.id)
    ])
    print(out)

def main():
    parser = argparse.ArgumentParser(description="SuperWA CLI Endpoint Controller")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # status
    p_status = subparsers.add_parser("status", help="Get accessibility & service status")
    p_status.set_defaults(func=cmd_status)

    # send-wa
    p_send = subparsers.add_parser("send-wa", help="Send WhatsApp message")
    p_send.add_argument("--phone", required=True, help="Recipient phone number")
    p_send.add_argument("--message", required=True, help="Message body")
    p_send.add_argument("--business", action="store_true", default=False, help="Use WA Business")
    p_send.set_defaults(func=cmd_send_wa)

    # call-wa
    p_call = subparsers.add_parser("call-wa", help="Make WhatsApp voice call")
    p_call.add_argument("--phone", required=True, help="Recipient phone number")
    p_call.add_argument("--business", action="store_true", default=False, help="Use WA Business")
    p_call.add_argument("--duration", type=int, default=15, help="Duration in seconds")
    p_call.add_argument("--include-dial-time", action="store_true", default=True, help="Include dial latency")
    p_call.set_defaults(func=cmd_call_wa)

    # post-status
    p_status_post = subparsers.add_parser("post-status", help="Publish WhatsApp Status story")
    p_status_post.add_argument("--message", default="", help="Status caption text")
    p_status_post.add_argument("--media", default="", help="Media file URI")
    p_status_post.set_defaults(func=cmd_post_status)

    # harvest-groups
    p_harvest = subparsers.add_parser("harvest-groups", help="Scan WhatsApp chats and harvest groups")
    p_harvest.add_argument("--business", action="store_true", default=False, help="Harvest WA Business groups")
    p_harvest.set_defaults(func=cmd_harvest)

    # dump-groups
    p_dump = subparsers.add_parser("dump-groups", help="Dump harvested groups to logcat")
    p_dump.set_defaults(func=cmd_dump_groups)

    # purge-corrupted-groups
    p_purge = subparsers.add_parser("purge-corrupted-groups", help="Remove individual chats mistakenly marked as groups")
    p_purge.set_defaults(func=cmd_purge_groups)

    # banking-shield
    p_shield = subparsers.add_parser("banking-shield", help="Configure protected banking package")
    p_shield.add_argument("--package", required=True, help="Package name (e.g. com.sbi.upi)")
    p_shield.add_argument("--disable", action="store_true", default=False, help="Remove package from shield")
    p_shield.set_defaults(func=cmd_shield)

    # run-task
    p_run = subparsers.add_parser("run-task", help="Immediately run scheduled task")
    p_run.add_argument("--id", type=int, required=True, help="Task ID to execute")
    p_run.set_defaults(func=cmd_run_task)

    parsed = parser.parse_args()
    parsed.func(parsed)

if __name__ == "__main__":
    main()
