import subprocess

devices = ['R9ZW105MFDR', 'D6VSYP4TL7GMI79T']
for dev in devices:
    # Enable shield
    res = subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_SET_BANKING_SHIELD',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
        '--es', 'package', 'com.sbi.upi',
        '--ez', 'enabled', 'true'
    ], capture_output=True, text=True)
    print(f'{dev} Enable shield result: {res.stdout.strip()}')

    # Check status
    res2 = subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_GET_STATUS',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver'
    ], capture_output=True, text=True)
    print(f'{dev} Status: {res2.stdout.strip()}')
