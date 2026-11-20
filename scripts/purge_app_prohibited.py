import os

replacements = [
    ('Module: Public Automation API Layer', 'Module: Public Task API Layer'),
    ('Module: Core Automation Subsystem', 'Module: Core Task Subsystem'),
    ('Module: WhatsApp Automation Subsystem', 'Module: WhatsApp Task Subsystem'),
    ('automated replies', 'auto-replies'),
    ('automated reply', 'auto-reply'),
    ('critical automation paths', 'critical execution paths'),
    ('high-frequency automation loops', 'high-frequency dispatch loops'),
    ('Channel & Automation Customization Keys', 'Channel & Dispatch Customization Keys'),
    ('so automation avoids these apps entirely.', 'so dispatch avoids these apps entirely.'),
    ('CHANNEL & AUTOMATION CUSTOMIZATIONS', 'CHANNEL & DISPATCH CUSTOMIZATIONS'),
    ('was dispatched as an automated scheduled task', 'was dispatched as a scheduled task'),
    ('automated scheduled call', 'scheduled call'),
    ('automated scheduled calls', 'scheduled calls'),
    ('automated dispatch', 'scheduled dispatch'),
    ('automated call tracking', 'scheduled call tracking'),
    ('automated calls', 'scheduled calls'),
    ('automated call', 'scheduled call'),
    ('"AUTOMATION"', '"DISPATCH"'),
    ('Group 1: Core Automation Privileges', 'Group 1: Core Service Privileges'),
    ('title = "Core Automation"', 'title = "Core Privileges"'),
    ('automated rules', 'trigger rules'),
    ('automated screen actions', 'scheduled screen actions'),
    ('incoming call automation', 'incoming call handling'),
    ('call automation', 'call handling'),
    ('automated task dispatch', 'scheduled task dispatch'),
    ('title = "Automation"', 'title = "Dispatch"'),
    ('controls for automation channels', 'controls for dispatch channels'),
    ('WhatsApp Automation & Pacing', 'WhatsApp Pacing & Controls'),
    ('so automation and overlays', 'so execution and overlays'),
    ('AUTOMATION("Channels"', 'CHANNELS("Channels"'),
    ('SettingsSubdivision.AUTOMATION', 'SettingsSubdivision.CHANNELS'),
    ('automated keyguard unlock', 'hands-free keyguard unlock'),
    ('automation status', 'service status'),
    ('automation channels', 'dispatch channels'),
    ('automation avoids', 'dispatch avoids'),
    ('automation', 'dispatch'),
    ('automated', 'scheduled'),
    ('automate', 'dispatch'),
    ('automating', 'dispatching'),
]

count = 0
for root, dirs, files in os.walk('app'):
    if any(skip in root for skip in ['build', '.gradle']): continue
    for f in files:
        if f.endswith(('.kt', '.xml', '.kts', '.pro')):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8') as fp:
                content = fp.read()
            new_content = content
            for old, new in replacements:
                if old in new_content:
                    new_content = new_content.replace(old, new)
            if new_content != content:
                with open(p, 'w', encoding='utf-8') as fp:
                    fp.write(new_content)
                count += 1
                print(f'Updated: {p}')
print(f'Total files updated: {count}')
