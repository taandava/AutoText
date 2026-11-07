#!/usr/bin/env python3
"""
Multi-Channel Automation Test Harness & System Trace Diagnostic Runner
Target: Helper (com.jmd.helper) on connected Android devices via ADB
"""

import argparse
import subprocess
import time
import sys
import os
import re

PACKAGE_NAME = "com.jmd.helper"
RECEIVER_NAME = f"{PACKAGE_NAME}/.receiver.TaskAlarmReceiver"

def run_adb(device_id: str, command: str, check: bool = True) -> str:
    adb_cmd = ["adb"]
    if device_id:
        adb_cmd.extend(["-s", device_id])
    adb_cmd.extend(command.split() if isinstance(command, str) else command)
    try:
        res = subprocess.run(adb_cmd, capture_output=True, text=True, check=check, encoding="utf-8", errors="replace")
        return res.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] ADB command failed: {' '.join(adb_cmd)}")
        print(f"Stderr: {e.stderr.strip()}")
        if check:
            raise
        return e.stdout.strip()

def get_connected_devices() -> list:
    out = run_adb("", "devices")
    lines = out.strip().split("\n")[1:]
    devices = []
    for line in lines:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices

def main():
    parser = argparse.ArgumentParser(description="Helper Multi-Channel Test Harness")
    parser.add_argument("--device", "-d", help="ADB Device Serial ID (optional if single device)")
    parser.add_argument("--numbers", "-n", default="9769602020,9930604542", help="Comma-separated test recipient numbers")
    parser.add_argument("--interval", "-i", type=int, default=10, help="Interval in seconds between tasks")
    parser.add_argument("--duration", type=int, default=10, help="Call duration in seconds")
    parser.add_argument("--pin", "-p", default="", help="Screen assist PIN (if device has PIN lock)")
    parser.add_argument("--auto-lock", action="store_true", default=True, help="Enable auto-lock after execution")
    parser.add_argument("--no-lock", action="store_true", help="Do not lock screen after setting up tasks")
    parser.add_argument("--monitor-seconds", "-m", type=int, default=150, help="Seconds to monitor execution logs")
    args = parser.parse_args()

    devices = get_connected_devices()
    if not devices:
        print("[FAIL] No connected ADB devices found! Please connect a device via USB/Wi-Fi.")
        sys.exit(1)

    device_id = args.device or devices[0]
    print(f"============================================================")
    print(f"🚀 HELPER MULTI-CHANNEL AUTOMATION TEST HARNESS")
    print(f"📱 Target Device: {device_id}")
    print(f"📞 Test Numbers: {args.numbers}")
    print(f"⏱️ Task Stagger Interval: {args.interval}s | Call Duration: {args.duration}s")
    if args.pin:
        print(f"🔐 Configured Screen Assist PIN: {args.pin}")
    print(f"============================================================\n")

    # Step 1: Wake up and verify basic connectivity
    print("[1/7] Verifying device power state and connectivity...")
    run_adb(device_id, "shell input keyevent 224") # Wake up screen
    time.sleep(1)

    # Step 2: Clear old logs on device
    print("[2/7] Clearing previous system logcat buffer...")
    run_adb(device_id, "logcat -c")

    # Step 3: Purge existing tasks to start completely fresh
    print("[3/7] Purging any existing tasks (com.jmd.helper.ACTION_DELETE_ALL_TASKS)...")
    cmd_delete = ["shell", "am", "broadcast", "-a", "com.jmd.helper.ACTION_DELETE_ALL_TASKS", "-n", RECEIVER_NAME]
    res_del = run_adb(device_id, cmd_delete)
    print(f"      -> {res_del}")
    time.sleep(1)

    # Step 4: Configure preferences (auto-lock, PIN)
    print("[4/7] Setting preferences (autoLock=true, screenAssistPin)...")
    cmd_pref_lock = ["shell", "am", "broadcast", "-a", "com.jmd.helper.ACTION_SET_PREF", "-n", RECEIVER_NAME, "--es", "key", "autoLock", "--ez", "value", "true"]
    run_adb(device_id, cmd_pref_lock)

    if args.pin:
        cmd_pref_pin = ["shell", "am", "broadcast", "-a", "com.jmd.helper.ACTION_SET_PREF", "-n", RECEIVER_NAME, "--es", "key", "screenAssistPin", "--es", "value", args.pin]
        run_adb(device_id, cmd_pref_pin)

    # Step 5: Schedule all 12 feature tasks
    print(f"[5/7] Scheduling all 12 channels for {args.numbers} at {args.interval}s stagger...")
    cmd_setup = [
        "shell", "am", "broadcast",
        "-a", "com.jmd.helper.ACTION_SETUP_ALL_TASKS",
        "-n", RECEIVER_NAME,
        "--es", "recipients", args.numbers,
        "--es", "message", "Live Automation Test • JMD Helper",
        "--ei", "staggerSeconds", str(args.interval),
        "--ei", "repeatIntervalSeconds", str(args.interval),
        "--ei", "callDurationSeconds", str(args.duration),
        "--ez", "includeDialTimeInCallDuration", "true",
        "--ez", "clearExisting", "true"
    ]
    res_setup = run_adb(device_id, cmd_setup)
    print(f"      -> {res_setup}")
    time.sleep(2)

    # Step 6: Lock screen to verify unattended wake & unlock capabilities
    if not args.no_lock:
        print("[6/7] 🔒 Locking screen via Power keyevent (26) to verify unattended wake/unlock assist...")
        run_adb(device_id, "shell input keyevent 26")
        time.sleep(1)
    else:
        print("[6/7] Screen lock skipped by user flag (--no-lock).")

    # Step 7: Live monitor execution logs
    print(f"\n[7/7] 📡 Monitoring live execution across all channels for {args.monitor_seconds}s...")
    print("      (Press Ctrl+C at any time to break and dump logs)\n")

    start_time = time.time()
    logged_actions = set()
    try:
        while time.time() - start_time < args.monitor_seconds:
            elapsed = int(time.time() - start_time)
            remaining = args.monitor_seconds - elapsed
            
            # Fetch recent Action logs from logcat
            log_out = run_adb(device_id, "logcat -d -t 150 -s Helper:I SequentialTaskEngine:I AlarmScheduler:I WACallEngine:I TaskAlarmReceiver:I HelperA11y:I", check=False)
            for line in log_out.splitlines():
                if "⚡ [ACTION]" in line:
                    action_part = line.split("⚡ [ACTION]")[-1].strip()
                    if action_part not in logged_actions:
                        logged_actions.add(action_part)
                        print(f"  [{elapsed:03d}s] ⚡ {action_part}")

            time.sleep(3)
    except KeyboardInterrupt:
        print("\n[INFO] Monitoring interrupted by user. Proceeding to dump logs...")

    # Step 8: Trigger Log Dump and Pull Diagnostics
    print("\n============================================================")
    print("📊 HARVESTING SYSTEM TRACES & DIAGNOSTIC AUDIT")
    print("============================================================")
    
    # Trigger ACTION_DUMP_LOGS
    print("Triggering ACTION_DUMP_LOGS on device...")
    cmd_dump = ["shell", "am", "broadcast", "-a", "com.jmd.helper.ACTION_DUMP_LOGS", "-n", RECEIVER_NAME]
    run_adb(device_id, cmd_dump)
    time.sleep(2)

    # Pull dump file if available
    dump_remote = "/sdcard/Android/data/com.jmd.helper/files/helper_execution_dump.txt"
    dump_local = "helper_execution_dump.txt"
    pull_res = run_adb(device_id, f"pull {dump_remote} {dump_local}", check=False)
    if "pulled" in pull_res or os.path.exists(dump_local):
        print(f"✅ Successfully pulled diagnostic dump: {dump_local}")
        with open(dump_local, "r", encoding="utf-8", errors="replace") as f:
            dump_content = f.read()
            print(f"   Dump Size: {len(dump_content)} bytes")
    else:
        print(f"ℹ️ Direct dump file note: {pull_res}. Extracting raw execution logs via logcat...")

    # Fetch last 300 lines of logcat for full audit
    raw_logcat = run_adb(device_id, "logcat -d -t 300", check=False)
    with open("recent_test_logcat.txt", "w", encoding="utf-8", errors="replace") as f:
        f.write(raw_logcat)
    print("✅ Captured recent raw logcat to 'recent_test_logcat.txt'")

    # Check for exceptions / fatal errors
    print("\n🔍 Scanning for runtime errors / crashes in logcat...")
    errors = []
    for line in raw_logcat.splitlines():
        if any(err in line for err in ["FATAL EXCEPTION", "AndroidRuntime: E", "com.jmd.helper.*Exception"]):
            errors.append(line)

    if errors:
        print(f"❌ Found {len(errors)} runtime error lines:")
        for e in errors[:5]:
            print(f"   {e}")
    else:
        print("✅ 0 Fatal Crashes or Runtime Exceptions detected!")

    print(f"\n🎉 Test session complete. Log files generated:")
    print(f"   - recent_test_logcat.txt")
    if os.path.exists("helper_execution_dump.txt"):
        print(f"   - helper_execution_dump.txt")

if __name__ == "__main__":
    main()

