#!/usr/bin/env python3
"""Fail if the stored-data contract of the Sensitivity class has been changed.

Page values are stored under the property names below. Renaming or retyping one
makes values already saved on pages unreadable by the banner, tab, backup and restore.
Run before every release: python3 tools/check-class-contract.py
"""
import sys
import xml.dom.minidom as minidom

CLASS_FILE = "src/main/resources/SensitivityBanner/Code/SensitivityClass.xml"
CLASS_NAME = "SensitivityBanner.Code.SensitivityClass"
REQUIRED = {
    "owner": "com.xpn.xwiki.objects.classes.StringClass",
    "security": "com.xpn.xwiki.objects.classes.StringClass",
    "country": "com.xpn.xwiki.objects.classes.StringClass",
}

doc = minidom.parse(CLASS_FILE)
root = doc.documentElement
errors = []
if root.getAttribute("reference") != CLASS_NAME:
    errors.append("document reference is %r, expected %r" % (root.getAttribute("reference"), CLASS_NAME))
cls = [c for c in root.childNodes if c.nodeType == 1 and c.tagName == "class"]
if not cls:
    errors.append("no <class> element")
else:
    if cls[0].getElementsByTagName("name")[0].firstChild.data != CLASS_NAME:
        errors.append("class name changed")
    props = {c.tagName: c for c in cls[0].childNodes if c.nodeType == 1 and c.getElementsByTagName("classType")}
    for name, ctype in REQUIRED.items():
        if name not in props:
            errors.append("property %r is missing" % name)
        elif props[name].getElementsByTagName("classType")[0].firstChild.data != ctype:
            errors.append("property %r changed type" % name)
if errors:
    print("CLASS CONTRACT BROKEN:\n  " + "\n  ".join(errors))
    sys.exit(1)
print("class contract ok: %s" % ", ".join(REQUIRED))


# ---------------------------------------------------------------------------------------------
# The contract between the Sensitivity Banner (core) and the temporary Sensitivity Migration tool.
# The migration may only use: the class above, the page SensitivityBanner.Code.Common, and the
# variables and macros that page defines. The core must never mention the migration.
# ---------------------------------------------------------------------------------------------
import glob
import re

CORE_DIR = "src/main/resources"
MIGRATION_DIR = "migration/src/main/resources"
ALLOWED_CORE_REFERENCES = {"SensitivityBanner.Code.SensitivityClass", "SensitivityBanner.Code.Common"}


def all_text(folder):
    parts = []
    for path in sorted(glob.glob(folder + "/**/*.xml", recursive=True)):
        parts.append((path, open(path, encoding="utf-8").read()))
    return parts


problems = []
core = all_text(CORE_DIR)
migration = all_text(MIGRATION_DIR)

for path, text in core:
    if "SensitivityMigration" in text:
        problems.append("core page %s mentions SensitivityMigration, the core must not depend on the migration tool" % path)

common = dict(core).get(CORE_DIR + "/SensitivityBanner/Code/Common.xml", "")
if not common:
    problems.append("core page Common.xml is missing")
defined_vars = set(re.findall(r"#set\s*\(\s*\$(sb\w+)", common)) | set(re.findall(r"#foreach\s*\(\s*\$(sb\w+)", common))
defined_macros = set(re.findall(r"#macro\s*\(\s*(sb\w+)", common))

used_vars, used_macros, referenced_pages = set(), set(), set()
for path, text in migration:
    own = set(re.findall(r"#set\s*\(\s*\$(sb\w+)", text)) | set(re.findall(r"#foreach\s*\(\s*\$(sb\w+)", text))
    for var in re.findall(r"\$!?\{?(sb\w+)", text):
        if var not in own:
            used_vars.add(var)
    used_macros |= set(re.findall(r"#(sb\w+)\s*\(", text))
    referenced_pages |= set(re.findall(r"SensitivityBanner\.[A-Za-z0-9_.]+", text))

for var in sorted(used_vars - defined_vars):
    problems.append("the migration uses $%s but Common.xml does not define it" % var)
for macro in sorted(used_macros - defined_macros):
    problems.append("the migration calls #%s but Common.xml does not define it" % macro)
for page in sorted(referenced_pages - ALLOWED_CORE_REFERENCES):
    problems.append("the migration references %s, which is outside the contract" % page)

if problems:
    print("CORE AND MIGRATION CONTRACT BROKEN:\n  " + "\n  ".join(problems))
    sys.exit(1)
print("migration contract ok: uses %s from Common, references only %s" % (
    ", ".join("$" + v for v in sorted(used_vars)) or "no variables", ", ".join(sorted(referenced_pages))))
