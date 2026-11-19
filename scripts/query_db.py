import subprocess

sql = "SELECT id, name, phone, source FROM dynamic_pool WHERE source='WA_GROUP' OR source='WA_BUSINESS_GROUP' OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%' ORDER BY id DESC LIMIT 100;\n"
cmd = ['adb', 'shell', 'run-as', 'com.jmd.superapp', 'sqlite3', '/data/data/com.jmd.superapp/databases/superapp.db']
res = subprocess.run(cmd, input=sql, capture_output=True, text=True)
print("STDOUT:")
print(res.stdout)
print("STDERR:")
print(res.stderr)
