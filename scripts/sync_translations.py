#!/usr/bin/env python3

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path("app_pojavlauncher/src/main/res")
SOURCE = ROOT / "values" / "strings.xml"


def load_strings(path):
    tree = ET.parse(path)
    root = tree.getroot()
    result = {}
    for node in root.findall("string"):
        name = node.get("name")
        if name:
            result[name] = node
    return result


def indent(elem, level=0):
    space = "\n" + "    " * level
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = space + "    "
        for child in elem:
            indent(child, level + 1)
        if not elem[-1].tail or not elem[-1].tail.strip():
            elem[-1].tail = space
    if level and (not elem.tail or not elem.tail.strip()):
        elem.tail = space


def escape_text(text):
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def extract_source(path):
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(
        r'<string\s+name="([^"]+)"(?:\s+[^>]*)?>(.*?)</string>',
        re.DOTALL,
    )
    return {name: value for name, value in pattern.findall(text)}


def sync_file(path, source):
    text = path.read_text(encoding="utf-8")
    existing = set(re.findall(r'<string\s+name="([^"]+)"', text))
    missing = [name for name in source if name not in existing]

    if not missing:
        return False

    additions = []
    for name in missing:
        value = source[name]
        additions.append(
            f'    <string name="{name}">{value}</string>'
        )

    if "</resources>" not in text:
        raise RuntimeError(f"Invalid resources XML: {path}")

    text = text.replace(
        "</resources>",
        "\n" + "\n".join(additions) + "\n</resources>",
    )

    path.write_text(text, encoding="utf-8")
    print(f"{path}: added {len(missing)} strings")
    return True


def main():
    if not SOURCE.exists():
        raise SystemExit(f"Source file not found: {SOURCE}")

    source = extract_source(SOURCE)
    changed = False

    for path in sorted(ROOT.glob("values-*/strings.xml")):
        if sync_file(path, source):
            changed = True

    if not changed:
        print("All translations are already synchronized.")


if __name__ == "__main__":
    main()
