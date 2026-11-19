#!/usr/bin/env python3
"""Builds the XAR without Maven, using only the Python standard library.

A XAR is a zip file holding the page XML files and a package.xml that lists the pages.
This script produces the same layout as the Maven build: every page under
src/main/resources at its relative path, then package.xml.

Usage: python3 tools/build-xar.py [OUTPUT.xar]
Default output: target/xwiki-sensitivity-banner-<version>.xar
The name, description and version come from pom.xml.
"""
import os
import sys
import zipfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(ROOT, "src", "main", "resources")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom_value(pom, tag):
    node = pom.getroot().find("m:" + tag, NS)
    if node is None or not (node.text or "").strip():
        sys.exit("pom.xml has no <%s>" % tag)
    return node.text.strip()


def main():
    pom = ET.parse(os.path.join(ROOT, "pom.xml"))
    name = pom_value(pom, "name")
    description = pom_value(pom, "description")
    version = pom_value(pom, "version")
    extension_id = pom_value(pom, "groupId") + ":" + pom_value(pom, "artifactId")
    artifact = pom_value(pom, "artifactId")

    pages = []
    for folder, _, files in os.walk(RESOURCES):
        for filename in files:
            if filename.endswith(".xml"):
                path = os.path.join(folder, filename)
                entry = os.path.relpath(path, RESOURCES).replace(os.sep, "/")
                reference = ET.parse(path).getroot().get("reference")
                if not reference:
                    sys.exit("%s has no reference attribute on <xwikidoc>" % entry)
                pages.append((entry, path, reference))
    if not pages:
        sys.exit("No pages found under " + RESOURCES)
    pages.sort()

    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "",
        "<package>",
        "<infos>",
        "<name>%s</name>" % escape(name),
        "<description>%s</description>" % escape(description),
        "<licence></licence>",
        "<author>XWiki.Admin</author>",
        "<extensionId>%s</extensionId>" % escape(extension_id),
        "<version>%s</version>" % escape(version),
        "<backupPack>false</backupPack>",
        "</infos>",
        "<files>",
    ]
    for _, _, reference in pages:
        lines.append('<file language="" defaultAction="0">%s</file>' % escape(reference))
    lines += ["</files>", "</package>", ""]
    package_xml = "\n".join(lines)

    output = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "target", "%s-%s.xar" % (artifact, version))
    os.makedirs(os.path.dirname(os.path.abspath(output)), exist_ok=True)
    stamp = (2026, 1, 1, 0, 0, 0)  # fixed, so the same sources always give the same file
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as xar:
        for entry, path, _ in pages:
            info = zipfile.ZipInfo(entry, stamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(path, "rb") as handle:
                xar.writestr(info, handle.read())
        info = zipfile.ZipInfo("package.xml", stamp)
        info.compress_type = zipfile.ZIP_DEFLATED
        xar.writestr(info, package_xml.encode("utf-8"))
    print("wrote %s (%d pages, version %s)" % (output, len(pages), version))


if __name__ == "__main__":
    main()
