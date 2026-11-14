# Data safety

Goal: values applied to pages must survive uninstalling, changing or upgrading this extension, including a bug in an update. Target scale discussed: around 30,000 pages.

## Where the data lives

Each page holds its own object of class `SensitivityBanner.Code.SensitivityClass` with the plain text properties `owner`, `security` and `country`. The values are in the wiki database against that page. Nothing is stored in the XAR, in the settings, or in the report.

## What was verified in the platform source

`XWikiHibernateStore.loadXWikiCollection` loads each object's properties from the `BaseProperty` table by object id, using the type stored with each value. The class definition is only consulted for an optional check (`bclass != null && ...`). So values load whether or not the class page exists. This was read from the code, not tested on a live wiki.

## Situations

| Situation | Expected result | Status |
|---|---|---|
| Reinstall or upgrade the XAR | Page values untouched. Only the extension pages and the settings object are overwritten. | Settings reset confirmed in design. Page values untouched is expected, not tested at scale. |
| Uninstall the extension | The extension pages go to the recycle bin. Page objects remain in the database. Reinstalling brings the class and tab back and values show again. | From code reading. Untested. |
| Rename or remove an option in settings | Pages keep the stored text. It shows as a retired value in the report. Saving the tab with the same value keeps it. | Designed. Partly tested. |
| Bug in the save handler | The handler only touches fields that were posted, refuses a request with none, and validates against the configured lists. Every save creates a page version, so the previous value is in page history. | Hardened. Untested on a live wiki. |
| Someone renames a property in the class | Stored values become unreadable. | Guarded by `tools/check-class-contract.py`. Run it before every release. |
| Someone deletes the `SensitivityBanner` space | Not recommended. Do not do this to clean up an import. | Effect on stored values not tested. |

## Rules for future changes

1. Never rename or retype `owner`, `security` or `country`, and never change the class name. Add new properties instead.
2. Run `tools/check-class-contract.py` and the Velocity syntax check before building a release.
3. Never make the save handler clear a field that was not posted.

## Before an upgrade or uninstall

1. Take a backup of the wiki VM or container (Proxmox Backup Server snapshot) so a rollback is possible.
2. Open the Sensitivity Report and note the exact number of entries shown under the table, and the counts in the summary.
3. Note your banner settings. Reinstalling resets them.
4. Upgrade or uninstall.
5. Open the report again and compare the numbers. They must match. If they do not, stop and restore.

## Not covered

- A rollback of one page to an older version also rolls back its values to that version's.
- There is no second copy of the values outside the wiki database. A Proxmox Backup Server snapshot restores the whole wiki, not individual values.
