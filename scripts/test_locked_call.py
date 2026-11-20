import subprocess, time

dev = 'D6VSYP4TL7GMI79T'
print(f'=== Testing Locked Phone Call on Realme ({dev}) ===')

# Turn off display to lock device
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '26'])
time.sleep(1.5)

# Verify keyguard status
res_dump = subprocess.run(['adb', '-s', dev, 'shell', 'dumpsys', 'window', 'policy'], capture_output=True, text=True)
is_showing = 'showing=true' in res_dump.stdout.lower() or 'keyguardservice' in res_dump.stdout.lower()
print(f'Screen locked state before call: {is_showing}')

# Dispatch call via TaskDispatchReceiver
print('Dispatching ACTION_MAKE_CALL (duration=8s)...')
t0 = time.time()
res_call = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_MAKE_CALL',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
    '--es', 'phone', '+919769602020',
    '--ei', 'duration', '8',
    '--ez', 'include_dial_time', 'true'
], capture_output=True, text=True)
elapsed = time.time() - t0
print(f'Call completed in {elapsed:.1f}s. Result: {res_call.stdout.strip()}')

# Check recent logcat
logs = subprocess.run([
    'adb', '-s', dev, 'shell', 'logcat', '-d', '-s', 'TelephonyCallEngine:I', '-s', 'TaskAccessibilityService:I', '-s', 'KeyguardAssistEngine:I'
], capture_output=True, text=True)
print('CALL LOGS:')
lines = logs.stdout.strip().split('\n')
for l in lines[-15:]:
    print(l)
