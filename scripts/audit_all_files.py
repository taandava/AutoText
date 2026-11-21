import os
import re
import sys
import xml.etree.ElementTree as ET

def run_comprehensive_audit():
    print("=" * 70)
    print("STARTING COMPREHENSIVE WHOLE-PROJECT REPOSITORY AUDIT (SUPERAPP)")
    print("=" * 70)

    prohibited_re = re.compile(r'\b(automation|automate|automating|automated)\b', re.I)
    direct_window_compat_re = re.compile(r'(?<!compat\.)(?<!fun )\b(setShowWhenLocked|setTurnScreenOn)\s*\(', re.I)

    stats = {
        'app_kt': 0,
        'app_xml': 0,
        'scripts': 0,
        'gradle_files': 0
    }

    issues = []

    for root, dirs, files in os.walk('.'):
        if any(skip in root for skip in ['.git', '.gradle', 'build', '.idea', 'node_modules', '.freebuff', '.kotlin', '.serena', '.vscode', 'supersms', 'superwa']):
            continue
        for f in files:
            p = os.path.join(root, f)
            rel = os.path.relpath(p, '.')

            is_kt = f.endswith('.kt')
            is_xml = f.endswith('.xml')
            is_py = f.endswith('.py')
            is_js = f.endswith('.js')
            is_gradle = f.endswith(('.gradle', '.gradle.kts'))

            if not (is_kt or is_xml or is_py or is_js or is_gradle):
                continue

            if rel.startswith('app'):
                if is_kt: stats['app_kt'] += 1
                elif is_xml: stats['app_xml'] += 1
            elif rel.startswith('scripts'):
                stats['scripts'] += 1
            if is_gradle:
                stats['gradle_files'] += 1

            is_app = rel.startswith('app')

            # 1. XML Parsing Validation
            if is_xml:
                try:
                    ET.parse(p)
                except ET.ParseError as pe:
                    issues.append((rel, 0, "XML Syntax Parse Error", str(pe)))

            # 2. Kotlin Package Verification
            if is_kt:
                clean_rel = os.path.dirname(rel).replace('\\', '/')
                for prefix in ['/src/main/java/', '/src/test/java/']:
                    if prefix in clean_rel:
                        clean_rel = clean_rel.split(prefix)[-1]
                        break
                expected_pkg_suffix = clean_rel.replace('/', '.')
                try:
                    with open(p, 'r', encoding='utf-8') as fp:
                        first_lines = [fp.readline() for _ in range(30)]
                    pkg_line = next((l.strip() for l in first_lines if l.strip().startswith('package ')), None)
                    if pkg_line:
                        actual_pkg = pkg_line.replace('package ', '').strip()
                        if actual_pkg != expected_pkg_suffix:
                            issues.append((rel, 1, f"Package Mismatch", f"Declared: '{actual_pkg}', expected: '{expected_pkg_suffix}'"))
                except Exception as e:
                    issues.append((rel, 0, "Kotlin Read Error", str(e)))

            try:
                with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
                    for line_no, line in enumerate(fp, 1):
                        # 3. Prohibited policy words in application module
                        if is_app and prohibited_re.search(line):
                            m = prohibited_re.search(line)
                            issues.append((rel, line_no, f"Prohibited word '{m.group(0)}'", line.strip()))

                        # 4. Direct window methods bypassing ActivityWindowCompat
                        if is_app and is_kt and not rel.endswith('ActivityWindowCompat.kt'):
                            if direct_window_compat_re.search(line):
                                issues.append((rel, line_no, "Direct Window Method (Use ActivityWindowCompat)", line.strip()))
            except Exception as e:
                issues.append((rel, 0, "Read Error", str(e)))

    print("\nFile Statistics Scanned:")
    print(f"  - App Kotlin Files:      {stats['app_kt']}")
    print(f"  - App XML Files:         {stats['app_xml']}")
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
