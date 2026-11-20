# Data safety

Goal: values applied to pages must survive uninstalling, changing or upgrading this extension, including a bug in an update. Target scale discussed: around 30,000 pages.

## Where the data lives

Each page holds its own object of class `SensitivityBanner.Code.SensitivityClass` with the plain text properties `owner`, `security` and `country`. The values are in the wiki database against that page. Nothing is stored in the XAR or in the settings.

## What was verified in the platform source

`XWikiHibernateStore.loadXWikiCollection` loads each object's properties from the `BaseProperty` table by object id, using the type stored with each value. The class definition is only consulted for an optional check. So values load whether or not the class page exists. `Document.getRecentRevisions(n)` returns versions newest first, and `$xwiki.getDocument(doc, revision)` loads an old version. Read from the code, not tested on a live wiki.

## Layers of protection

1. **Values live on the pages.** Reinstalling, upgrading or uninstalling the XAR only touches the extension's own pages.
2. **Class contract guard.** `tools/check-class-contract.py` fails if `owner`, `security` or `country` is renamed or retyped.
3. **Safer save handler.** It only updates fields that were posted, refuses a request with none, validates against the configured lists, and every save creates a page version.
4. **Restore from history (single page rollback).** Rolling a page back to a version before it had values leaves it without values. The Sensitivity tab then shows the newest earlier version that had values with a "Restore these values" button. The server re-reads the history itself and ignores posted values. It only works when the page currently has no values and scans the newest 30 versions.
5. **Backup file outside the wiki.** Administration, Content, Sensitivity Backup downloads one JSON file of every page's values. The browser reads the data in chunks of 500, so large wikis and proxy timeouts are not a problem.
6. **Restore with a dry run.** The same section restores from a backup file in chunks of 100 pages. A dry run reports what would change and changes nothing. The restore buttons stay disabled until a dry run of that same file has finished. "Restore blanks only" never touches a page that already has values. "Restore and overwrite differences" also replaces differing values after a confirmation. A backup row with no values is ignored, so a restore cannot blank a page. Every restored page gets a normal page version with the comment "Restored sensitivity from backup".

## Situations

| Situation | Expected result |
|---|---|
| Reinstall, upgrade or uninstall the XAR | Page values untouched. Reinstalling brings the class and tab back. Settings reset on reinstall. |
| Rename or remove an option in settings | Pages keep the stored text. The banner still shows it and the tab marks it as retired. |
| A page is rolled back to an older version | Values go back to that version's. If it had none, use the tab's restore from history, or restore from a backup. |
| Values lost on many pages | Dry run, then restore blanks only from the latest backup. |
| Bug in an update clears or changes values | Restore from the latest backup. Page history also holds the previous values. |
| Someone renames a property in the class | Stored values become unreadable. Guarded by the contract check. Run it before every release. |
| Someone deletes the `SensitivityBanner` space | Not recommended. Effect on stored values not tested. |

## Routine

1. Download a backup regularly, and always immediately before an upgrade or uninstall. Store it with your Proxmox Backup Server backups, not only on the wiki host.
2. Also take a snapshot of the wiki VM or container before an upgrade.
3. Open the downloaded backup file and note its `count`. After the upgrade download a second backup and compare the `count`. They must match.
4. If they do not, stop, run a dry run against the backup, and restore blanks only.

## Migration writes

The Sensitivity Migration tool (a separate, temporary extension) writes values to pages and can remove the old macro from page source. Both steps start with a dry run, only touch pages that pass the same checks as a normal save, never overwrite existing values, and leave a page version for every change. Take a backup of the wiki and download a Sensitivity Backup file before running it. Removal is the only step that edits page content, so it only acts where the stored values already match the macro.

## Rules for future changes

1. Never rename or retype `owner`, `security` or `country`, and never change the class name. Add new properties instead.
2. Never change the backup file `format` number without keeping the old format readable.
3. Run `tools/check-class-contract.py` and the Velocity syntax check before building a release.
4. Never make the save handler or restore clear a field that was not posted.

## Limits

- Restore history only looks at the newest 30 versions of a page. Older values need a backup.
- A restore matches pages by name. A page renamed or moved after the backup shows as no longer existing in the dry run.
- A backup holds only pages that had at least one value at that moment. Pages cleared on purpose are not in it.
- The backup file lists page names, which can themselves be sensitive. Treat the file accordingly. It is not encrypted.
- Any edit made to a page after the backup is not in it. A backup is only as recent as its date.
- Verification status: the Velocity handlers pass a syntax check only and have not been run on a live wiki. The backup page script was tested in a browser against a mocked server. Test the backup and a dry run on your instance before relying on this.
