import os
import re

def audit_module(module_name, prohibited_words, leak_words):
    issues = {
        'prohibited': [],
        'leaks': [],
        'reminders': []
    }
    src_dir = os.path.join(module_name, 'src')
    if not os.path.exists(src_dir):
        return issues

    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if not f.endswith(('.kt', '.xml', '.gradle', '.kts')):
                continue
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, module_name)
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
                    content = fp.read()
            except Exception as e:
                continue

            # 1. Prohibited words
            for word in prohibited_words:
                matches = list(re.finditer(r'\b' + re.escape(word) + r'\b', content, re.IGNORECASE))
                if matches:
                    lines = [content[:m.start()].count('\n') + 1 for m in matches]
                    issues['prohibited'].append({
                        'word': word,
                        'file': rel_path,
                        'lines': lines[:8]
                    })

            # 2. Cross-app leaks
            for leak in leak_words:
                matches = list(re.finditer(r'\b' + re.escape(leak) + r'\b', content, re.IGNORECASE))
                if matches:
                    lines = [content[:m.start()].count('\n') + 1 for m in matches]
                    issues['leaks'].append({
                        'leak': leak,
                        'file': rel_path,
                        'lines': lines[:8]
                    })

            # 3. Reminder remnants
            rem_matches = list(re.finditer(r'\breminder\b', content, re.IGNORECASE))
            if rem_matches:
                lines = [content[:m.start()].count('\n') + 1 for m in rem_matches]
                issues['reminders'].append({
                    'file': rel_path,
                    'lines': lines[:8]
                })

    return issues

print("="*60)
print("AUDITING SUPERSMS")
print("="*60)
sms_issues = audit_module('supersms', ['automation', 'automate', 'automating', 'automated'], ['whatsapp', 'whatsappbusiness', 'w4b'])
print(f"Prohibited word matches: {len(sms_issues['prohibited'])}")
for item in sms_issues['prohibited'][:10]:
    print(f"  - '{item['word']}' in {item['file']} (lines {item['lines']})")

print(f"\nWhatsApp leak matches in SuperSMS: {len(sms_issues['leaks'])}")
for item in sms_issues['leaks'][:10]:
    print(f"  - '{item['leak']}' in {item['file']} (lines {item['lines']})")

print(f"\nReminder matches in SuperSMS: {len(sms_issues['reminders'])}")
for item in sms_issues['reminders']:
    print(f"  - in {item['file']} (lines {item['lines']})")

print("\n" + "="*60)
print("AUDITING SUPERWA")
print("="*60)
wa_issues = audit_module('superwa', ['automation', 'automate', 'automating', 'automated'], ['callsimulation', 'call_simulation', 'simslot', 'smsquota'])
print(f"Prohibited word matches: {len(wa_issues['prohibited'])}")
for item in wa_issues['prohibited'][:10]:
    print(f"  - '{item['word']}' in {item['file']} (lines {item['lines']})")

print(f"\nSMS/Call leak matches in SuperWA: {len(wa_issues['leaks'])}")
for item in wa_issues['leaks'][:10]:
    print(f"  - '{item['leak']}' in {item['file']} (lines {item['lines']})")

print(f"\nReminder matches in SuperWA: {len(wa_issues['reminders'])}")
for item in wa_issues['reminders']:
    print(f"  - in {item['file']} (lines {item['lines']})")
