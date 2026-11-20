import subprocess

devices = ['R9ZW105MFDR', 'D6VSYP4TL7GMI79T']
for dev in devices:
    res = subprocess.run([
        'adb', '-s', dev, 'shell', 'am', 'broadcast',
        '-a', 'com.jmd.superapp.ACTION_SEND_SMS',
        '-n', 'com.jmd.superapp/com.jmd.superapp.receiver.TaskDispatchReceiver',
        '--es', 'phone', '+919769602020',
        '--es', 'message', 'SuperApp live SMS test'
    ], capture_output=True, text=True)
    print(f'{dev} SMS result: {res.stdout.strip()}')
