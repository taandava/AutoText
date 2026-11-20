import subprocess, time, json

devices = [
    {'id': 'R9ZW105MFDR', 'name': 'Samsung Galaxy A04', 'pin': '2020', 'swipe_y': 1200},
    {'id': 'D6VSYP4TL7GMI79T', 'name': 'Realme 7', 'pin': '9619', 'swipe_y': 1800}
]

def unlock_device(dev, pin, swipe_y):
    # Wake
    subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '224'])
    time.sleep(0.4)
    subprocess.run(['adb', '-s', dev, 'shell', 'input', 'swipe', '360', str(swipe_y), '360', '400', '200'])
    time.sleep(0.4)
    subprocess.run(['adb', '-s', dev, 'shell', 'input', 'text', pin])
    subprocess.run(['adb', '-s', dev, 'shell', 'input', 'keyevent', '66'])
    time.sleep(0.8)

for d in devices:
    dev = d['id']
    name = d['name']
    pin = d['pin']
    swipe_y = d['swipe_y']
    print(f'\n========================================')
    print(f'Starting test suite on {name} ({dev})')
    print(f'========================================')
    unlock_device(dev, pin, swipe_y)

    # Launch app
    subprocess.run(['adb', '-s', dev, 'shell', 'monkey', '-p', 'com.jmd.superapp', '-c', 'android.intent.category.LAUNCHER', '1'], capture_output=True)
    time.sleep(1)

    # Initial Status
    res = subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
    ], capture_output=True, text=True)
    print(f'Initial Status: {res.stdout.strip()}')

    # 1. Harvest WhatsApp groups
    print('Harvesting WhatsApp groups (GROUPS_ONLY)...')
    subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_HARVEST_WHATSAPP',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
        '--es', 'package', 'com.whatsapp',
        '--es', 'target', 'GROUPS_ONLY',
        '--es', 'channel', 'WHATSAPP_GROUP'
    ], capture_output=True)
    time.sleep(7)

    # 2. Harvest WhatsApp Business groups
    print('Harvesting WhatsApp Business groups (GROUPS_ONLY)...')
    subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_HARVEST_WHATSAPP',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
        '--es', 'package', 'com.whatsapp.w4b',
        '--es', 'target', 'GROUPS_ONLY',
        '--es', 'channel', 'WHATSAPP_BUSINESS_GROUP'
    ], capture_output=True)
    time.sleep(9)

    # Status after group harvests
    res = subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
    ], capture_output=True, text=True)
    print(f'Status after group harvests: {res.stdout.strip()}')

print('\n========================================')
print('All device tests executed')
print('========================================')
