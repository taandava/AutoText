import os
import re

patterns = [
    (re.compile(r'AppButton\s*\(\s*text\s*=\s*"([^"]+)"'), 'AppButton'),
    (re.compile(r'AppBadge\s*\(\s*text\s*=\s*"([^"]+)"'), 'AppBadge'),
    (re.compile(r'AppChip\s*\([^)]*label\s*=\s*"([^"]+)"'), 'AppChip'),
    (re.compile(r'actionLabel\s*=\s*"([^"]+)"'), 'actionLabel'),
    (re.compile(r'buttonText\s*=\s*"([^"]+)"'), 'buttonText'),
    (re.compile(r'SystemIndicator\s*\([^)]*name\s*=\s*"([^"]+)"'), 'SystemIndicator.name'),
    (re.compile(r'SystemIndicator\s*\([^)]*actionLabel\s*=\s*"([^"]+)"'), 'SystemIndicator.actionLabel'),
]

for module in ['app']:
    print(f"=== CHECKING UI LABELS IN {module} ===")
    violations = []
    for root, _, files in os.walk(module):
        if 'build' in root:
            continue
        for f in files:
            if not f.endswith('.kt'):
                continue
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
                content = fp.read()
                for pat, comp in patterns:
                    for m in pat.finditer(content):
                        text = m.group(1).strip()
                        # Clean out dynamic formatters like %02d or $var
                        words = [w for w in text.split() if not w.startswith('%') and not w.startswith('$') and w not in ['•', '-', '|']]
                        if len(words) > 2:
                            violations.append((path, comp, text))
    print(f"{module} label violations (> 2 words): {len(violations)}")
    for p, c, t in violations[:20]:
        print(f"  [{c}] \"{t}\" in {os.path.basename(p)}")
