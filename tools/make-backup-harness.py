#!/usr/bin/env python3
"""Builds a static HTML harness from the Sensitivity Backup admin sheet so its script can be tested in a browser.

Usage: python3 tools/make-backup-harness.py OUTDIR   (writes OUTDIR/harness.html)
Then:  node tools/backup-ui-test.js OUTDIR           (needs the playwright package)
The test serves the harness from a fake http origin and mocks both handler endpoints. It tests the browser side only.
"""
import re
import sys
import xml.dom.minidom as minidom

doc = minidom.parse("src/main/resources/SensitivityBanner/Code/BackupSheet.xml")
content = doc.getElementsByTagName("content")[0].firstChild.data
blocks = re.findall(r'\{\{html clean="false"\}\}(.*?)\{\{/html\}\}', content, re.S)
page = "<!doctype html><html><body>" + "".join(blocks) + "</body></html>"
page = re.sub(r'data-backup-url="[^"]*"', 'data-backup-url="/mock/backup?outputSyntax=plain"', page)
page = re.sub(r'data-restore-url="[^"]*"', 'data-restore-url="/mock/restore?outputSyntax=plain"', page)
page = re.sub(r'data-token="[^"]*"', 'data-token="TOKEN123"', page)
open(sys.argv[1] + "/harness.html", "w").write(page)
print("wrote", sys.argv[1] + "/harness.html")
