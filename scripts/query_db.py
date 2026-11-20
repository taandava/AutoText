import subprocess

devices = [
    ('R9ZW105MFDR', 'Samsung Galaxy A04'),
    ('D6VSYP4TL7GMI79T', 'Realme 7')
]

for dev_id, dev_name in devices:
    print(f"\n==========================================")
    print(f"Device: {dev_name} ({dev_id})")
    print(f"==========================================")
    sql = "SELECT source, normalized_number, name FROM dynamic_pool WHERE source LIKE '%GROUP%' OR normalized_number LIKE 'group:%';"
    res = subprocess.run(
        ['adb', '-s', dev_id, 'shell', f'run-as com.jmd.superapp sqlite3 /data/user/0/com.jmd.superapp/databases/superapp_v8.db "{sql}"'],
        capture_output=True,
        text=True
    )
    if res.stdout.strip():
        for line in res.stdout.strip().splitlines():
            print(f"  {line}")
    else:
        print("  (No groups or error: " + res.stderr.strip() + ")")
