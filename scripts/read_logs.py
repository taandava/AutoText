import subprocess

dev = 'R9ZW105MFDR'
# Get PID of com.jmd.superapp
pid_res = subprocess.run(['adb', '-s', dev, 'shell', 'pidof', 'com.jmd.superapp'], capture_output=True, text=True)
pid = pid_res.stdout.strip()
print(f'SuperApp PID on Samsung: {pid}')

if pid:
    logs = subprocess.run(['adb', '-s', dev, 'shell', f'logcat --pid={pid} -d -t 60'], capture_output=True, text=True)
    print('LOGS:')
    print(logs.stdout)
