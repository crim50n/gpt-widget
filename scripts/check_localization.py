#!/usr/bin/env python3
"""Validate translation keys, array sizes and Java format placeholders (stdlib only)."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdfoxegcb]", re.IGNORECASE)


def catalog(path):
    result = {}
    for element in ET.parse(path).getroot():
        if element.get("translatable") == "false":
            continue
        name = element.attrib["name"]
        if name in result:
            raise ValueError(f"{path}: duplicate key {name}")
        values = ["".join(child.itertext()) for child in element] if element.tag == "string-array" else ["".join(element.itertext())]
        result[name] = (element.tag, values)
    return result


def placeholders(value):
    return sorted(PLACEHOLDER.findall(value.replace("%%", "")))


def main():
    base = catalog(RES / "values/strings.xml")
    errors = []
    translations = sorted(RES.glob("values-*/strings.xml"))
    for path in translations:
        translated = catalog(path)
        for name in sorted(base.keys() - translated.keys()):
            errors.append(f"{path.parent.name}: missing {name}")
        for name in sorted(translated.keys() - base.keys()):
            errors.append(f"{path.parent.name}: unknown key {name}")
        for name in base.keys() & translated.keys():
            source_type, source = base[name]
            target_type, target = translated[name]
            if source_type != target_type or len(source) != len(target):
                errors.append(f"{path.parent.name}: resource structure differs for {name}")
                continue
            for original, localized in zip(source, target):
                if placeholders(original) != placeholders(localized):
                    errors.append(f"{path.parent.name}: placeholders differ for {name}")
    for path in (ROOT / "app/src/main/java").rglob("*.java"):
        if re.search(r"[\u0400-\u04ff]", path.read_text(encoding="utf-8")):
            errors.append(f"{path.relative_to(ROOT)}: untranslated Cyrillic in Java source")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Localization OK: {len(base)} resources, {len(translations)} translation(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
