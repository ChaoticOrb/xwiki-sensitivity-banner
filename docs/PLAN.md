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
| `Code.Admin` | ConfigurableClass | Adds a "Sensitivity Banner" section to wiki administration. The settings object (`Code.ConfigClass`) is stored on this page, because the platform form reads the first object of the configuration class from the page that holds the ConfigurableClass object (`XWiki/ConfigurableClass.xml`, `$app.getObject(...)`). |
| `Code.Common` | Velocity include | Reads the settings object from `Code.Admin`, falls back to built-in defaults if it is missing, parses option lists. |
| `Code.BannerUIX` | UIX | Renders the banner at `content.header.after`. |
| `Code.TabUIX` | UIX | Renders the Sensitivity tab with three dropdowns. |
| `Code.Save` | Handler | Validates and saves the selections, then redirects back. |
| `Code.ReportAdmin` and `Code.ReportSheet` | ConfigurableClass and sheet | "Sensitivity Report" admin section: a Live Data table of all pages with values, plus lists of pages missing sensitivity and of values no longer in the configured lists. |

## Behaviour

- Option lists are one value per line, optionally `Value|#colour`. Defaults: Owner (Management, Senior Management, Standard Staff), Security (Sensitive red, Open green), Country (UK, Europe).
- Banner is on by default. The shipped settings object has `enabled=1`, and code defaults apply if the object is ever missing.
- Reinstalling or upgrading the XAR overwrites `Code.Admin` and resets the settings to defaults. Note them before reinstalling.
- Nothing selected shows the configured missing text (default "Page missing sensitivity"). Partly filled shows only the chosen values.
- Spaces `XWiki`, `SensitivityBanner` and `AppWithinMinutes` are excluded by default (configurable). Non-existent pages never show it.
- Two checkboxes, both on by default: hide on terminal pages (any page not named `WebHome`, assuming the default home page name), and hide on Application Within Minutes application pages (pages with an `AppWithinMinutes.LiveTableClass` object, which the platform's `CreateApplication.xml` uses as the application descriptor).
- A page that already has a sensitivity value always shows its banner, even if excluded. The Sensitivity tab is not hidden on excluded pages.
- Not verified: whether blog posts on the target wiki are terminal pages. If they are, the terminal checkbox hides the banner on posts without a value.
- The stored value is the option text. Renaming an option in config leaves existing pages holding the old text. The banner still shows it, and the tab marks it as retired.
- Save requires edit rights on the target page and a valid CSRF token, and rejects values not in the configured lists.

## Not verified yet (needs a test on your instance)

1. Version: the test instance runs XWiki 17.4, and the project pins 17.4.0 in `pom.xml` (`xwiki.version`). Running on the latest release has not been tested. Pages use `xwiki/2.1` syntax and `{{html clean="false"}}`, which are old and stable, but the packaging plugin must match your major version.
2. That `XWiki.ConfigurableClass` with only `configurationClass` set (no `codeToExecute`) renders the form as expected.
3. (Resolved) The first build wrote object fields without the `<property>` wrapper, so the UIX registrations were dropped on import and the tab never appeared. Objects now use the same structure as a working 17.4 XAR, with the class definitions embedded.
4. Rights: wiki-scoped UIX pages run Velocity under the author of the UIX page, so the installing user needs script and admin rights. Normal for an XAR installed through Extension Manager as admin.
5. Blog post pages. They are ordinary documents rendered by the same template, so the banner should appear. The Blog application's own space is not excluded. Not tested.

## Later phases

- Optional: show the banner in edit mode.
- Optional: a page-picker listing pages missing sensitivity (HQL over `BaseObject`).
- Optional: Java component replacing the Velocity handler if rights or performance become a problem.

## Lessons from the first test on 17.4

- Velocity cannot call a method on a string literal (`"$x".trim()` is a syntax error). Assign to a variable first.
- Velocity in every page is now run through the Velocity 2.3 parser before building. Parsing proves syntax only, not behaviour on a live wiki.
- UIX pages hold a short description as their own page content. The real template lives only in the UIX object, so viewing the UIX page directly no longer runs it.
- The admin section first failed with "No object of class ... found in page SensitivityBanner.Code.Admin" because the settings object was not on that page. It now ships there with the defaults.

## Report design notes

- The table uses `{{liveData source='liveTable' sourceParameters='className=...'}}`, the same pattern the platform uses (`Release/WebHome.xml`, `Menu/WebHome.xml`). Rights filtering comes from the platform.
- The missing and retired lists use partial XWQL (`from doc.object(Class) as obj where ...`) and are diffed in Velocity. Complete HQL statements for non-programming users depend on the instance's query validator configuration, so they are avoided.
- Lists read at most 5000 matching pages per query, show at most 500 missing pages, and check at most 1000 pages for retired values. The page says when it hits the cap.
- Not tested on a live wiki. The first things to check: the table renders, the missing list excludes the pages you expect, and a deliberately renamed option shows up under retired values.
