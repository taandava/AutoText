#!/usr/bin/env python3
"""
SuperSMS CLI Endpoint Tool
Provides direct CLI control over SuperSMS via ADB broadcasts with dynamic device discovery.
Usage:
    python scripts/supersms_cli.py status
    python scripts/supersms_cli.py diagnostics
    python scripts/supersms_cli.py batch [--campaign-id 1]
    python scripts/supersms_cli.py send-sms --phone "+919876543210" --message "Test SMS" [--sim 0]
    python scripts/supersms_cli.py call --phone "+919876543210" [--duration 15] [--sim 0]
    python scripts/supersms_cli.py simulate-call [--name "Office"] [--phone "+919876543210"] [--skin PIXEL]
    python scripts/supersms_cli.py sync-pool
    python scripts/supersms_cli.py run-task --id 123
"""

import argparse
import os
import subprocess
import sys

def get_target_device(cli_device=None):
    if cli_device:
        return cli_device
    env_serial = os.environ.get("ANDROID_SERIAL")
    if env_serial:
        return env_serial.strip()
    try:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
        for raw_line in res.stdout.strip().splitlines():
            line = raw_line.strip()
            if not line or line.startswith("*") or "List of devices" in line:
                continue
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                return parts[0]
    except Exception as e:
        print(f"[Warn] Device discovery error: {e}", file=sys.stderr)
    return "10BF1G0HCD0024R"

def run_adb(cmd_args, device_id):
    full_cmd = ["adb", "-s", device_id] + cmd_args
    res = subprocess.run(full_cmd, capture_output=True, text=True)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def cmd_status(args):
    target = get_target_device(args.device)
    print(f"[*] Querying SuperSMS Status on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_GET_STATUS",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver"
    ], target)
    print(out)
    if err:
        print(f"[Error]: {err}", file=sys.stderr)

def cmd_diagnostics(args):
    target = get_target_device(args.device)
    print(f"[*] Running SuperSMS Device Diagnostics on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_DEVICE_DIAGNOSTICS",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver"
    ], target)
    print(out)
    if err:
        print(f"[Error]: {err}", file=sys.stderr)

def cmd_batch(args):
    target = get_target_device(args.device)
    print(f"[*] Dispatching Campaign Batch (campaign_id={args.campaign_id}) on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_RUN_CAMPAIGN_BATCH",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--el", "campaign_id", str(args.campaign_id)
    ], target)
    print(out)
    if err:
        print(f"[Error]: {err}", file=sys.stderr)

def cmd_send_sms(args):
    target = get_target_device(args.device)
    print(f"[*] Sending SMS to {args.phone} via SIM {args.sim} on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SEND_SMS",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--es", "message", args.message,
        "--ei", "sim_slot", str(args.sim)
    ], target)
    print(out)

def cmd_call(args):
    target = get_target_device(args.device)
    print(f"[*] Placing Phone Call to {args.phone} (duration={args.duration}s, sim={args.sim}) on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_MAKE_CALL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "phone", args.phone,
        "--ei", "duration", str(args.duration),
        "--ei", "sim_slot", str(args.sim),
        "--ez", "include_dial_time", str(args.include_dial_time).lower()
    ], target)
    print(out)

def cmd_simulate_call(args):
    target = get_target_device(args.device)
    print(f"[*] Simulating Inbound Call from {args.name} ({args.phone}) on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SIMULATE_CALL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--es", "caller_name", args.name,
        "--es", "caller_number", args.phone,
        "--es", "skin", args.skin,
        "--ei", "timeout_seconds", str(args.timeout)
    ], target)
    print(out)

def cmd_sync_pool(args):
    target = get_target_device(args.device)
    print(f"[*] Syncing Contact Pool in SuperSMS on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_SYNC_POOL",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver"
    ], target)
    print(out)

def cmd_run_task(args):
    target = get_target_device(args.device)
    print(f"[*] Triggering Task #{args.id} in SuperSMS on {target}...")
    out, err, code = run_adb([
        "shell", "am", "broadcast",
        "-a", "com.jmd.supersms.ACTION_DISPATCH_TASK",
        "-n", "com.jmd.supersms/.receiver.TaskDispatchReceiver",
        "--el", "task_id", str(args.id)
    ], target)
    print(out)

def main():
    parser = argparse.ArgumentParser(description="SuperSMS CLI Endpoint Controller")
    parser.add_argument("--device", "-d", default=None, help="Target device serial (auto-detected if omitted)")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # status
    p_status = subparsers.add_parser("status", help="Get service & telephony status")
    p_status.set_defaults(func=cmd_status)

    # diagnostics
    p_diag = subparsers.add_parser("diagnostics", help="Query deep device & app diagnostics")
    p_diag.set_defaults(func=cmd_diagnostics)

    # batch
    p_batch = subparsers.add_parser("batch", help="Trigger daily outreach campaign batch immediately")
    p_batch.add_argument("--campaign-id", type=int, default=1, help="Campaign ID to execute")
    p_batch.set_defaults(func=cmd_batch)

    # send-sms
    p_send = subparsers.add_parser("send-sms", help="Send cellular SMS")
    p_send.add_argument("--phone", required=True, help="Recipient phone number")
    p_send.add_argument("--message", required=True, help="SMS message text")
    p_send.add_argument("--sim", type=int, default=0, help="SIM card slot index (0 or 1)")
    p_send.set_defaults(func=cmd_send_sms)

    # call
    p_call = subparsers.add_parser("call", help="Make cellular phone call")
    p_call.add_argument("--phone", required=True, help="Recipient phone number")
    p_call.add_argument("--duration", type=int, default=15, help="Call duration in seconds")
    p_call.add_argument("--sim", type=int, default=0, help="SIM card slot index")
    p_call.add_argument("--include-dial-time", action="store_true", default=True, help="Include dial latency")
    p_call.set_defaults(func=cmd_call)

    # simulate-call
    p_sim = subparsers.add_parser("simulate-call", help="Simulate incoming phone call UI")
    p_sim.add_argument("--name", default="Office / Boss", help="Caller display name")
    p_sim.add_argument("--phone", default="+91 98765 43210", help="Caller phone number")
    p_sim.add_argument("--skin", default="PIXEL", help="Screen skin (PIXEL, SAMSUNG, IPHONE)")
    p_sim.add_argument("--timeout", type=int, default=30, help="Ringing timeout seconds")
    p_sim.set_defaults(func=cmd_simulate_call)

    # sync-pool
    p_sync = subparsers.add_parser("sync-pool", help="Scan and sync contacts into dynamic pool")
    p_sync.set_defaults(func=cmd_sync_pool)

    # run-task
    p_run = subparsers.add_parser("run-task", help="Immediately run scheduled task")
    p_run.add_argument("--id", type=int, required=True, help="Task ID to execute")
    p_run.set_defaults(func=cmd_run_task)

    parsed = parser.parse_args()
    parsed.func(parsed)

if __name__ == "__main__":
    main()
