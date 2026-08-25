#!/usr/bin/env python3

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path("app_pojavlauncher/src/main/res")
SOURCE = ROOT / "values" / "strings.xml"
STRING_RE = re.compile(
    r'<string\b[^>]*\bname\s*=\s*(["\'])([^"\']+)\1[^>]*>(.*?)</string>',
    re.DOTALL,
)


def validate_xml(path):
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        raise RuntimeError(f"Invalid XML in {path}: {exc}") from exc


def extract_strings(path):
    text = path.read_text(encoding="utf-8")
    return [(match.group(2), match.group(3)) for match in STRING_RE.finditer(text)]


def sync_file(path, source):
    validate_xml(path)
    text = path.read_text(encoding="utf-8")
    existing = {name for name, _ in extract_strings(path)}
    missing = [(name, value) for name, value in source if name not in existing]

    if not missing:
        return False

    additions = "\n".join(
        f'    <string name="{name}">{value}</string>'
        for name, value in missing
    )

    marker = "</resources>"
    if text.count(marker) != 1:
        raise RuntimeError(f"Invalid resources XML: {path}")

    updated = text.replace(marker, f"\n{additions}\n{marker}", 1)
    path.write_text(updated, encoding="utf-8")
    print(f"{path}: added {len(missing)} missing strings")
    return True


def main():
    if not SOURCE.exists():
        raise SystemExit(f"Source file not found: {SOURCE}")

    validate_xml(SOURCE)
    source = extract_strings(SOURCE)

    names = [name for name, _ in source]
    if len(names) != len(set(names)):
        raise RuntimeError("Duplicate string names found in the source strings.xml")

    changed = False
    for path in sorted(ROOT.glob("values-*/strings.xml")):
        changed |= sync_file(path, source)

    if changed:
        print("Translations synchronized successfully.")
    else:
        print("All translations are already synchronized.")


if __name__ == "__main__":
    main()
