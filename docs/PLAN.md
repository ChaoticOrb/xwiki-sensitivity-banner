# XWiki Sensitivity Banner: plan

## Requirement

A banner at the top of standard pages and blog posts showing a combination of Owner, Security and Country values chosen per page. Values must be configurable in the UI. On by default. Shows "Page missing sensitivity" when nothing is selected.

## What was verified in the XWiki platform source (18.10.0-SNAPSHOT, read from github.com/xwiki/xwiki-platform)

| Need | Mechanism | Evidence |
|---|---|---|
| Banner under the page title, above content | UIX extension point `org.xwiki.platform.template.content.header.after` | Rendered in `flamingo/contentview.vm` via `$services.uix.renderExtensions('org.xwiki.platform.template.content.header.after', 'html/5.0')`. Used by `MissingRequiredRightWarningUIExtension.java`. |
| Custom tab in the bottom tab bar (Comments, Attachments, History, Information) | UIX extension point `org.xwiki.plaftorm.template.docextra` (the misspelling "plaftorm" is real) | `flamingo/docextra.vm` and `loaddocextrauix.vm`. Parameters read: `show`, `title`, `name`, `itemnumber`, `shortcut`, `order`. Covered by `DocExtraTabsIT.java`. |
| Admin UI for configuration | `XWiki.ConfigurableClass` object on an admin page | Pattern used by `XWiki.SearchAdmin`, notifications, etc. |
| Save with CSRF check | `$services.csrf.isTokenValid($request.form_token)` | Used in AppWithinMinutes pages. |

## Design decision: tab placement

The built-in Information tab is hard coded in `docextra.vm` (`informationinline.vm`). There is no extension point to add fields inside it. The supported route is a new tab, so the dropdowns live in a **Sensitivity** tab next to Information, not inside it. Overriding `informationinline.vm` would put the dropdowns inside Information but is a template override that breaks on upgrades. Not done.

## Components

All pages live in the `SensitivityBanner` space (hidden).

| Page | Type | Purpose |
|---|---|---|
| `Code.SensitivityClass` | XClass | Per page data: `owner`, `security`, `country` (plain strings, so they are searchable with HQL). |
| `Code.ConfigClass` | XClass | Settings: `enabled`, `missingText`, `ownerValues`, `securityValues`, `countryValues`, `excludedSpaces`. |
| `Code.Admin` | ConfigurableClass | Adds a "Sensitivity Banner" section to wiki administration. Stores the config object in `XWiki.XWikiPreferences`. |
| `Code.Common` | Velocity include | Reads config, applies built-in defaults when no config object exists yet, parses option lists. |
| `Code.BannerUIX` | UIX | Renders the banner at `content.header.after`. |
| `Code.TabUIX` | UIX | Renders the Sensitivity tab with three dropdowns. |
| `Code.Save` | Handler | Validates and saves the selections, then redirects back. |

## Behaviour

- Option lists are one value per line, optionally `Value|#colour`. Defaults: Owner (Management, Senior Management, Standard Staff), Security (Sensitive red, Open green), Country (UK, Europe).
- Banner is on with no configuration, because defaults are in code and an unsaved config object is treated as enabled.
- Nothing selected shows the configured missing text (default "Page missing sensitivity"). Partly filled shows only the chosen values.
- Spaces `XWiki` and `SensitivityBanner` are excluded by default (configurable). Non-existent pages never show it.
- The stored value is the option text. Renaming an option in config leaves existing pages holding the old text. The banner still shows it, and the tab marks it as retired.
- Save requires edit rights on the target page and a valid CSRF token, and rejects values not in the configured lists.

## Not verified yet (needs a test on your instance)

1. Which XWiki version you run. The project pins a version in `pom.xml` (`xwiki.version`). Pages use `xwiki/2.1` syntax and `{{html clean="false"}}`, which are old and stable, but the packaging plugin must match your major version.
2. That `XWiki.ConfigurableClass` with only `configurationClass` set (no `codeToExecute`) renders the form as expected.
3. That UIX objects without an embedded `<class>` block import cleanly (class definitions are present on the instance).
4. Rights: wiki-scoped UIX pages run Velocity under the author of the UIX page, so the installing user needs script and admin rights. Normal for an XAR installed through Extension Manager as admin.
5. Blog post pages. They are ordinary documents rendered by the same template, so the banner should appear. The Blog application's own space is not excluded. Not tested.

## Later phases

- Optional: show the banner in edit mode.
- Optional: a page-picker listing pages missing sensitivity (HQL over `BaseObject`).
- Optional: Java component replacing the Velocity handler if rights or performance become a problem.
