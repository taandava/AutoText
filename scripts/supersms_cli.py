#!/usr/bin/env python3
"""
SuperSMS CLI Endpoint Tool
Provides direct CLI control over SuperSMS via ADB broadcasts.
Usage:
    python scripts/supersms_cli.py status
    python scripts/supersms_cli.py send-sms --phone "+919876543210" --message "Test SMS" [--sim 0]
    python scripts/supersms_cli.py call --phone "+919876543210" [--duration 15] [--sim 0]
    python scripts/supersms_cli.py simulate-call [--name "Office"] [--phone "+919876543210"] [--skin PIXEL]
    python scripts/supersms_cli.py sync-pool
    python scripts/supersms_cli.py run-task --id 123
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
    print(f"[*] Querying SuperSMS Status on {DEVICE_ID}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_GET_STATUS",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver"
    ])
    print(out)
    if err:
        print(f"[Error]: {err}", file=sys.stderr)

def cmd_send_sms(args):
    print(f"[*] Sending SMS to {args.phone} via SIM {args.sim}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SEND_SMS",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--es", "message", args.message,
        "--ei", "sim_slot", str(args.sim)
    ])
    print(out)

def cmd_call(args):
    print(f"[*] Placing Phone Call to {args.phone} (duration={args.duration}s)...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_MAKE_CALL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--ei", "duration", str(args.duration),
        "--ei", "sim_slot", str(args.sim),
        "--ez", "include_dial_time", str(args.include_dial_time).lower()
    ])
    print(out)

def cmd_simulate_call(args):
    print(f"[*] Simulating Inbound Call from {args.name} ({args.phone})...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SIMULATE_CALL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "caller_name", args.name,
        "--es", "caller_number", args.phone,
        "--es", "skin", args.skin,
        "--ei", "timeout_seconds", str(args.timeout)
    ])
    print(out)

def cmd_sync_pool(args):
    print(f"[*] Syncing Contact Pool in SuperSMS...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SYNC_POOL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver"
    ])
    print(out)

def cmd_run_task(args):
    print(f"[*] Triggering Task #{args.id} in SuperSMS...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_DISPATCH_TASK",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--el", "task_id", str(args.id)
    ])
    print(out)

def main():
    parser = argparse.ArgumentParser(description="SuperSMS CLI Endpoint Controller")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # status
    p_status = subparsers.add_parser("status", help="Get service & telephony status")
    p_status.set_defaults(func=cmd_status)

    # send-sms
    p_sms = subparsers.add_parser("send-sms", help="Send cellular SMS")
    p_sms.add_argument("--phone", required=True, help="Recipient phone number")
    p_sms.add_argument("--message", required=True, help="Message body")
    p_sms.add_argument("--sim", type=int, default=0, help="SIM Slot (0 or 1)")
    p_sms.set_defaults(func=cmd_send_sms)

    # call
    p_call = subparsers.add_parser("call", help="Place cellular phone call")
    p_call.add_argument("--phone", required=True, help="Recipient phone number")
    p_call.add_argument("--duration", type=int, default=10, help="Duration in seconds")
    p_call.add_argument("--sim", type=int, default=0, help="SIM Slot (0 or 1)")
    p_call.add_argument("--include-dial-time", action="store_true", default=True, help="Include dial latency")
    p_call.set_defaults(func=cmd_call)

    # simulate-call
    p_sim = subparsers.add_parser("simulate-call", help="Simulate incoming call")
    p_sim.add_argument("--name", default="Office / Boss", help="Caller display name")
    p_sim.add_argument("--phone", default="+91 98765 43210", help="Caller phone number")
    p_sim.add_argument("--skin", default="PIXEL", help="Display skin (PIXEL, SAMSUNG)")
    p_sim.add_argument("--timeout", type=int, default=30, help="Ringing timeout in seconds")
    p_sim.set_defaults(func=cmd_simulate_call)

    # sync-pool
    p_sync = subparsers.add_parser("sync-pool", help="Scan and sync contact pool")
    p_sync.set_defaults(func=cmd_sync_pool)

    # run-task
    p_run = subparsers.add_parser("run-task", help="Immediately run scheduled task")
    p_run.add_argument("--id", type=int, required=True, help="Task ID to execute")
    p_run.set_defaults(func=cmd_run_task)

    parsed = parser.parse_args()
    parsed.func(parsed)

if __name__ == "__main__":
    main()
