# Changelog

## Unreleased

- Removed the Sensitivity Report admin section (table of pages, missing pages list, retired values list). It is not needed for migration. The Sensitivity Banner settings, Sensitivity Backup section, tab and banner are unchanged.
- If you are upgrading by importing the XAR over an existing install, the two old report pages are not deleted by the import. Delete `SensitivityBanner.Code.ReportAdmin` and `SensitivityBanner.Code.ReportSheet` by hand.

## 0.1.0

First release, a pre-release. Built against XWiki 17.4.0 and tested by the author on one 17.4 instance. Other versions are untested.

- Banner under the page title showing Owner, Security and Country, with "Page missing sensitivity" when nothing is selected. On by default.
- Sensitivity tab at the bottom of each page with a dropdown per field.
- Administration, Sensitivity Banner: option lists with optional colours, missing text, on/off, excluded spaces, hide on terminal pages and on Application Within Minutes pages.
- Administration, Sensitivity Report: all pages with values, pages missing sensitivity, and values no longer in the configured lists.
- Administration, Sensitivity Backup: JSON backup download and restore with a dry run first. Restore from page history in the tab.
- Safeguards for stored values: see `docs/DATA-SAFETY.md`.

Known limits: reinstalling the XAR resets the banner settings to defaults. The restore from history only scans the newest 30 versions of a page.
