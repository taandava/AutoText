import os
import re
import sys

def run_comprehensive_audit():
    print("=" * 70)
    print("STARTING COMPREHENSIVE WHOLE-PROJECT REPOSITORY AUDIT")
    print("=" * 70)

    prohibited_re = re.compile(r'\b(automation|automate|automating|automated)\b', re.I)
    reminder_re = re.compile(r'\breminder(s)?\b', re.I)
    supersms_wa_re = re.compile(r'\b(whatsapp|w4b|whatsappbusiness)\b', re.I)
    supersms_a11y_re = re.compile(r'\b(accessibility|device_admin|deviceadmin)\b', re.I)
    superwa_sms_re = re.compile(r'\b(SmsManager|SEND_SMS|RECEIVE_SMS|READ_SMS|CALL_PHONE)\b')

    stats = {
        'supersms_kt': 0,
        'supersms_xml': 0,
        'superwa_kt': 0,
        'superwa_xml': 0,
        'scripts': 0,
        'gradle_files': 0
    }

    issues = []

    for root, dirs, files in os.walk('.'):
        if any(skip in root for skip in ['.git', '.gradle', 'build', '.idea', 'node_modules', '.freebuff', '.kotlin', '.serena', '.vscode']):
            continue
        for f in files:
            p = os.path.join(root, f)
            rel = os.path.relpath(p, '.')

            is_kt = f.endswith('.kt')
            is_xml = f.endswith('.xml')
            is_py = f.endswith('.py')
            is_gradle = f.endswith(('.gradle', '.gradle.kts'))

            if not (is_kt or is_xml or is_py or is_gradle):
                continue

            if rel.startswith('supersms'):
                if is_kt: stats['supersms_kt'] += 1
                elif is_xml: stats['supersms_xml'] += 1
            elif rel.startswith('superwa'):
                if is_kt: stats['superwa_kt'] += 1
                elif is_xml: stats['superwa_xml'] += 1
            elif rel.startswith('scripts'):
                stats['scripts'] += 1
            if is_gradle:
                stats['gradle_files'] += 1

            is_sms = rel.startswith('supersms')
            is_wa = rel.startswith('superwa')

            try:
                with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                    for line_no, line in enumerate(fp, 1):
                        # 1. Prohibited policy words (only in code modules, exclude audit scripts checking for them)
                        if (is_sms or is_wa) and prohibited_re.search(line):
                            m = prohibited_re.search(line)
                            issues.append((rel, line_no, f"Prohibited word '{m.group(0)}'", line.strip()))

                        # 2. Legacy reminders
                        if (is_sms or is_wa) and reminder_re.search(line):
                            m = reminder_re.search(line)
                            issues.append((rel, line_no, f"Reminder match '{m.group(0)}'", line.strip()))

                        # 3. SuperSMS leaks (WhatsApp, A11y, DeviceAdmin)
                        if is_sms:
                            m_wa = supersms_wa_re.search(line)
                            if m_wa:
                                issues.append((rel, line_no, f"SuperSMS WhatsApp leak '{m_wa.group(0)}'", line.strip()))
                            m_a11y = supersms_a11y_re.search(line)
                            if m_a11y:
                                issues.append((rel, line_no, f"SuperSMS A11y leak '{m_a11y.group(0)}'", line.strip()))

                        # 4. SuperWA leaks (SmsManager, SMS permissions, CALL_PHONE)
                        if is_wa:
                            m_sms = superwa_sms_re.search(line)
                            if m_sms:
                                issues.append((rel, line_no, f"SuperWA Telephony/Dialer leak '{m_sms.group(0)}'", line.strip()))
            except Exception as e:
                issues.append((rel, 0, "Read Error", str(e)))

    print("\nFile Statistics Scanned:")
    print(f"  - SuperSMS Kotlin Files: {stats['supersms_kt']}")
    print(f"  - SuperSMS XML Files:    {stats['supersms_xml']}")
    print(f"  - SuperWA Kotlin Files:  {stats['superwa_kt']}")
    print(f"  - SuperWA XML Files:     {stats['superwa_xml']}")
    print(f"  - Tooling Scripts:       {stats['scripts']}")
    print(f"  - Gradle Build Files:    {stats['gradle_files']}")
    print(f"  - TOTAL FILES SCANNED:   {sum(stats.values())}")

    print("\nAudit Results:")
    print(f"  - Total Policy/Architecture Issues: {len(issues)}")
    if issues:
        for rel, line_no, desc, line in issues:
            print(f"    * [{desc}] {rel}:{line_no} -> {line}")
        return False
    else:
        print("  - ALL FILES 100% INVARIANT-COMPLIANT & SECURE!")
        return True

if __name__ == '__main__':
    success = run_comprehensive_audit()
    sys.exit(0 if success else 1)
