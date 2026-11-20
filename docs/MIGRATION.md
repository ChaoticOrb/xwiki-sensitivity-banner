# Migrating the Confluence sensitivity macro

One time transfer of the values from the old Confluence user macro into the Owner, Security and Country fields, in two separate steps. This is a separate, temporary extension, **Sensitivity Migration**, built from `migration/`. Import it for the migration and delete it afterwards. It appears under Administration, Content, Sensitivity Migration.

## Installing and removing it

1. Install the Sensitivity Banner first. The migration screen shows an error and no buttons if it is missing.
2. Import the migration XAR (`xwiki-sensitivity-migration-<version>.xar`) as an admin.
3. When the migration is finished and checked, delete the `SensitivityMigration` space: open its home page and delete it including its child pages. The section disappears from the administration. The sensitivity values already stored on pages are not affected, so download a Sensitivity Backup file before and after and compare its `count` if you want proof.

The migration uses only the sensitivity class and the shared `Common` page of the banner. `tools/check-class-contract.py` checks that.

## What it expects

The pages arrive in XWiki through Confluence Migrator Pro. How Migrator Pro writes a custom user macro into the page source was not verified, because no migrated page was available when this was built. The scan therefore accepts these forms, and the macro name and the three parameter names can be changed on the screen before a run:

| Form in the page source | Example |
|---|---|
| XWiki macro | `{{sensitivity owner="Management" security="Sensitive" country="UK"/}}` |
| XWiki macro with the bridge prefix | `{{confluence_sensitivity owner="Management" .../}}` |
| XWiki macro with a body | `{{sensitivity owner="Management"}}anything{{/sensitivity}}` (the body is ignored) |
| Literal Confluence markup | `{sensitivity:owner=Management\|security=Sensitive\|country=UK}` |

Parameter names are not case sensitive. Only parameters on the macro call can be read. A Confluence export holds the macro call, not the HTML the macro renders, so extra HTML emitted by the old macro does not reach XWiki. If any page in Confluence takes its values from page properties or labels instead of macro parameters, add explicit parameters to those pages before exporting.

## Before you start

1. Take a snapshot or backup of the wiki.
2. Download a Sensitivity Backup file if any pages already have values.
3. Try it on a small test space first. Expect to adjust the macro or parameter names after the first dry run.

## Step 1: copy the values

Press "Dry run: migration". It reads every page whose source mentions the macro name and changes nothing. For each page:

- Values are matched to the Sensitivity Banner option lists ignoring case and repeated spaces. The option text from your lists is what is stored, so `senior management` becomes `Senior Management`.
- A page is skipped and listed if any value is not in your lists. Nothing is guessed or stored as a retired value.
- A page is skipped if it already has sensitivity values, if two macros on it disagree, if it has no macro with a value, or if you have no edit right on it.
- A macro with only some parameters is accepted. The missing fields stay empty.
- Two identical macros on a page, even in different forms, count as one.

The wiki then repeats the checks on the rows it would write and reports whether it agrees. The apply button stays disabled until a dry run of the same settings has finished. Changing any setting throws the dry run away. Applying asks for confirmation, writes each page with a normal page version ("Migrated sensitivity from the old Confluence macro"), and leaves the old macro on the page.

## Step 2: remove the old macro

Run this only after you have looked at the migrated pages. Press "Dry run: remove macros". A macro is only removed from a page whose stored values already equal what the macro says. Pages that were not migrated, whose values differ, whose macro text can no longer be found, or that you cannot edit are skipped and listed. A macro alone on its line is removed together with its line break. Each page gets a page version with the comment "Removed the old sensitivity macro after migration", so every change can be undone from page history.

## Limits

- Page translations are not scanned. Only the default language of each page is.
- A Confluence block macro written as `{sensitivity:...}text{sensitivity}` leaves the closing `{sensitivity}` behind, because it carries no values. It needs a manual clean up.
- Macro text inside a code block counts as a macro and would be read and removed.
- The pre-filter is a text search of the page source for the macro name, so it reads the whole content column. On a very large wiki the first request can take a while.
- Between step 1 and step 2, pages still show the old macro as the bridge macro renders it, which may be an error message for an unknown macro.
- The reading and parsing happen in the browser and the writing in the wiki. The pages that are written must still pass the same value and rights checks as a normal save.

## Verification status

The parser, the dry run gate and the whole two step flow are tested in Chromium against a mock of the wiki handler (`tools/migrate-ui-test.js`). The handler logic runs under the Velocity 2.3 engine against stand-ins for XWiki pages (`tools/handler-test`), with null assignment both allowed and disallowed. Not tested on a live wiki: the candidate query (`doc.content like` through XWQL), the real page content and save calls, and how Migrator Pro writes the macro. Test on a few pages first.
