# XWiki Sensitivity Banner

Adds a banner to the top of pages and blog posts showing Owner, Security and Country values chosen per page. Status: first draft, built but not yet tested on a live XWiki instance. See `docs/PLAN.md` for the design, what was verified against the XWiki source, and what still needs testing.

## Build

    mvn package

This produces `target/xwiki-sensitivity-banner-0.1.0-SNAPSHOT.xar`. The `xwiki.version` property in `pom.xml` selects the XAR tooling version (currently 16.10.2). Set it to your XWiki version.

## Install

Upload the XAR in Extension Manager (Administer Wiki, Extensions, Advanced Search, or Import in the wiki administration) and install it on the wiki. Install as an admin, because the banner pages contain Velocity.

## Use

Open any page and pick values in the **Sensitivity** tab at the bottom (next to Comments, Attachments, History, Information). The banner shows under the page title. With nothing selected it shows "Page missing sensitivity".

Edit the allowed values, the missing text and the excluded spaces under Administration, Sensitivity Banner. One value per line, optionally followed by a colour, for example `Sensitive|#c0392b`.
