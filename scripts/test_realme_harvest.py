import subprocess, time

dev = 'D6VSYP4TL7GMI79T'
print(f'=== Testing Group Harvest on Realme {dev} ===')
# Wake display
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '224'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'swipe', '540', '1800', '540', '600', '200'])
time.sleep(0.5)
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'text', '9619'])
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '66'])
time.sleep(1)

# Check status before
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
], capture_output=True, text=True)
print(f'Realme status before: {res.stdout.strip()}')

# Trigger group harvest for WhatsApp
print('Triggering WhatsApp group harvest on Realme...')
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_HARVEST_WHATSAPP',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
    '--es', 'package', 'com.whatsapp',
    '--es', 'target', 'GROUPS_ONLY',
    '--es', 'channel', 'WHATSAPP_GROUP'
], capture_output=True, text=True)
print(f'WhatsApp harvest broadcast: {res.stdout.strip()}')

# Wait for scanner to scan WhatsApp chat list
time.sleep(7)

# Check status after WhatsApp harvest
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
], capture_output=True, text=True)
print(f'Realme status after WhatsApp harvest: {res.stdout.strip()}')

# Trigger group harvest for WhatsApp Business
print('Triggering WhatsApp Business group harvest on Realme...')
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_HARVEST_WHATSAPP',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
    '--es', 'package', 'com.whatsapp.w4b',
    '--es', 'target', 'GROUPS_ONLY',
    '--es', 'channel', 'WHATSAPP_BUSINESS_GROUP'
], capture_output=True, text=True)
print(f'WhatsApp Business harvest broadcast: {res.stdout.strip()}')

time.sleep(7)

# Check final status
res = subprocess.run([
    'adb', '-s', dev, 'shell', 'am', 'broadcast',
    '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
    '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
], capture_output=True, text=True)
print(f'Realme final status: {res.stdout.strip()}')
