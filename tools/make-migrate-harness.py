#!/usr/bin/env python3
"""Builds a static HTML harness from the Sensitivity Migration admin sheet (the separate migration extension) so its script can be tested in a browser.

Usage: python3 tools/make-migrate-harness.py OUTDIR   (writes OUTDIR/migrate-harness.html)
Then:  node tools/migrate-ui-test.js OUTDIR           (needs the playwright package)
"""
import re
import sys
import xml.dom.minidom as minidom

doc = minidom.parse("migration/src/main/resources/SensitivityMigration/Code/Sheet.xml")
content = doc.getElementsByTagName("content")[0].firstChild.data
blocks = re.findall(r'\{\{html clean="false"\}\}(.*?)\{\{/html\}\}', content, re.S)
page = "<!doctype html><html><body>" + "".join(blocks) + "</body></html>"
page = re.sub(r'data-url="[^"]*"', 'data-url="/mock/mg?outputSyntax=plain"', page)
page = re.sub(r'data-token="[^"]*"', 'data-token="TOKEN123"', page)
open(sys.argv[1] + "/migrate-harness.html", "w").write(page)
print("wrote", sys.argv[1] + "/migrate-harness.html")
