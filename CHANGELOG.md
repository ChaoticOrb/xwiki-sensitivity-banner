# Changelog

## Unreleased

### Sensitivity Banner (next release 0.2.0)

- Removed the Sensitivity Report admin section (table of pages, missing pages list, retired values list). It is not needed for migration.
- Hardened the handlers so a page without a sensitivity object is never confused with the previous page in a batch, whatever the Velocity null assignment setting.
- Added `tools/build-xar.py`, which builds a XAR with Python only, no Maven. Its pages are byte for byte identical to the Maven build.
- If you import the XAR over an existing install, the two old report pages are not deleted by the import. Delete `SensitivityBanner.Code.ReportAdmin` and `SensitivityBanner.Code.ReportSheet` by hand. If you imported a build that contained the migration section, also delete `SensitivityBanner.Code.MigrateAdmin`, `SensitivityBanner.Code.MigrateSheet` and `SensitivityBanner.Code.MigrateHandler`.

### Sensitivity Migration (new, separate extension, 0.1.0)

- A temporary tool, in its own XAR and its own `SensitivityMigration` space, for the one time transfer of Owner, Security and Country from the old Confluence `sensitivity` user macro, in two steps (copy the values, then remove the macro), each with a dry run first. See `docs/MIGRATION.md`.
- Reads the macro as Migrator Pro writes it, `{{confluence_sensitivity Sensitivity="..." Security="..." Country="..." /}}`. Each field has a box for the parameter name or names that carry it, separated by commas. The dry run lists the parameter names it found, warns about names that are not mapped to any field, and shows how many pages would have no value for each field.
- Needs the Sensitivity Banner installed and says so when it is missing. It uses only the sensitivity class and the shared `Common` page, and `tools/check-class-contract.py` enforces that.
- Remove it when the migration is finished by deleting the `SensitivityMigration` space. Stored sensitivity values are not affected.

## 0.1.0

First release, a pre-release. Built against XWiki 17.4.0 and tested by the author on one 17.4 instance. Other versions are untested.

- Banner under the page title showing Owner, Security and Country, with "Page missing sensitivity" when nothing is selected. On by default.
- Sensitivity tab at the bottom of each page with a dropdown per field.
- Administration, Sensitivity Banner: option lists with optional colours, missing text, on/off, excluded spaces, hide on terminal pages and on Application Within Minutes pages.
- Administration, Sensitivity Report: all pages with values, pages missing sensitivity, and values no longer in the configured lists.
- Administration, Sensitivity Backup: JSON backup download and restore with a dry run first. Restore from page history in the tab.
- Safeguards for stored values: see `docs/DATA-SAFETY.md`.

Known limits: reinstalling the XAR resets the banner settings to defaults. The restore from history only scans the newest 30 versions of a page.
