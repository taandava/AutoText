import subprocess, time

dev = 'R9ZW105MFDR'
print(f'=== Testing Status Posting on Samsung ({dev}) ===')

# Ensure device is awake and unlocked
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '224'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'swipe', '360', '1200', '360', '400', '200'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'text', '2020'])
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '66'])
time.sleep(1)

# Dispatch status post via broadcast
print('Dispatching ACTION_POST_STATUS...')
t0 = time.time()
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_POST_STATUS',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
    '--es', 'package', 'com.whatsapp',
    '--es', 'message', 'SuperApp Live Test Status'
], capture_output=True, text=True)
elapsed = time.time() - t0
print(f'Status post result (in {elapsed:.1f}s): {res.stdout.strip()}')

# Check recent logcat
logs = subprocess.run([
    'adb', '-s', dev, 'shell', 'logcat', '-d', '-s', 'WAStatusEngine:I', '-s', 'TaskAccessibilityService:I', '-s', 'TaskFeatureApi:I'
], capture_output=True, text=True)
print('STATUS LOGS:')
lines = logs.stdout.strip().split('\n')
for l in lines[-15:]:
    print(l)
