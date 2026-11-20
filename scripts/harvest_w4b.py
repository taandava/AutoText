import subprocess, time

dev = 'R9ZW105MFDR'
# Wake display
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '224'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'swipe', '360', '1200', '360', '400', '200'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'text', '2020'])
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '66'])
time.sleep(1)

# Trigger group harvest for WhatsApp Business
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_HARVEST_WHATSAPP',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
    '--es', 'package', 'com.whatsapp.w4b',
    '--es', 'target', 'GROUPS_ONLY',
    '--es', 'channel', 'WHATSAPP_BUSINESS_GROUP'
], capture_output=True, text=True)
print(f'Triggered WhatsApp Business harvest: {res.stdout.strip()}')

time.sleep(6)

# Query status
res2 = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
], capture_output=True, text=True)
print(f'STATUS AFTER W4B HARVEST: {res2.stdout.strip()}')
