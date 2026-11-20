# XWiki Sensitivity Banner

This repository holds two separate extensions.

- **Sensitivity Banner** (repository root) is the permanent one: the banner, the per page tab, the settings and the backup and restore.
- **Sensitivity Migration** (`migration/`) is a temporary tool for the one time transfer of values from the old Confluence macro. Import it for the migration, then delete it. It needs the Sensitivity Banner installed.

Adds a banner to the top of pages and blog posts showing Owner, Security and Country values chosen per page. Status: working on XWiki 17.4 (tested by the author on one instance). Not tested on other versions. See `docs/PLAN.md` for the design, what was verified against the XWiki source, and what still needs testing.

## Build

    mvn package
    (cd migration && mvn package)

The first command produces `target/xwiki-sensitivity-banner-<version>.xar` and the second `migration/target/xwiki-sensitivity-migration-<version>.xar`. The `xwiki.version` property in each `pom.xml` selects the XAR tooling version (currently 17.4.0, matching the test instance). Set it to your XWiki version.

To build without Maven, use only Python 3:

    python3 tools/build-xar.py
    python3 tools/build-xar.py migration

This writes the same files to the same places. The pages inside are byte for byte identical to the Maven build, and the same sources always give the same file. It needs no downloads. It does not run the checks, which are described in `tools/README.md`.

## Install

Upload the XAR in Extension Manager (Administer Wiki, Extensions, Advanced Search, or Import in the wiki administration) and install it on the wiki. Install as an admin, because the pages contain Velocity. Install the Sensitivity Banner first. The migration tool is only needed during the migration, see `docs/MIGRATION.md`.

## Use

Open any page and pick values in the **Sensitivity** tab at the bottom (next to Comments, Attachments, History, Information). The banner shows under the page title. With nothing selected it shows "Page missing sensitivity".

Edit the allowed values, the missing text and the excluded spaces under Administration, Sensitivity Banner. One value per line, optionally followed by a colour, for example `Sensitive|#c0392b`.

Under Administration, Content, the Sensitivity Banner section also controls whether the banner is hidden on terminal pages (any page not named WebHome) and on Application Within Minutes application pages. A page that already has a value always shows its banner.

Reinstalling the XAR resets the banner settings to their defaults, so note them first.

The Sensitivity Backup section downloads a JSON backup of every page's values and restores from one, with a dry run first. The Sensitivity tab can restore values from an earlier page version when a rollback has removed them.

See `docs/DATA-SAFETY.md` for how stored values are protected and what to check before upgrading, and `tools/README.md` for the pre-release checks.

## Licence

MIT. See `LICENSE`.
