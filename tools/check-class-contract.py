#!/usr/bin/env python3
"""Fail if the stored-data contract of the Sensitivity class has been changed.

Page values are stored under the property names below. Renaming or retyping one
makes values already saved on pages unreadable by the banner, tab and report.
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
