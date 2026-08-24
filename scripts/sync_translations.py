#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path("app_pojavlauncher/src/main/res")
SOURCE=ROOT/"values/strings.xml"

def strings(path):
    return {x.get("name") for x in ET.parse(path).getroot().findall("string") if x.get("name")}

def source_strings(path):
    text=path.read_text(encoding="utf-8")
    return re.findall(r'<string\b[^>]*\bname="([^"]+)"[^>]*>(.*?)</string>',text,re.S)

def sync(path, source):
    text=path.read_text(encoding="utf-8")
    existing=strings(path)
    missing=[(n,v) for n,v in source if n not in existing]
    if not missing:return False
    additions="\n".join(f'    <string name="{n}">{v}</string>' for n,v in missing)
    text=text.replace("</resources>",f"\n{additions}\n</resources>",1)
    path.write_text(text,encoding="utf-8")
    print(f"{path}: added {len(missing)} missing strings")
    return True

def main():
    if not SOURCE.exists():raise SystemExit(f"Missing {SOURCE}")
    source=source_strings(SOURCE)
    changed=False
    for path in sorted(ROOT.glob("values-*/strings.xml")):
        changed|=sync(path,source)
    print("Translations synchronized." if changed else "All translations are already synchronized.")

if __name__=="__main__":main()
