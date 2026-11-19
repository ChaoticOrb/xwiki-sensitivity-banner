# Tools

Run both before releasing. Neither needs a running XWiki.

## Building without Maven

    python3 tools/build-xar.py [OUTPUT.xar]

Builds the XAR from `src/main/resources` using only the Python standard library, with the name, description and version taken from `pom.xml`. A XAR is a zip of the page XML files plus a `package.xml` that lists them. The output matches the Maven build, page for page, and is reproducible.

## Class contract check

    python3 tools/check-class-contract.py

Fails if the stored data fields of `SensitivityBanner.Code.SensitivityClass` (`owner`, `security`, `country`) were renamed or retyped. Values already saved on pages are stored under those names.

## Velocity syntax check

Parses every `{{velocity}}` block in the page XML with Velocity 2.3. It catches syntax errors such as a method call on a string literal. It does not prove the pages behave correctly on a wiki.

    mkdir -p /tmp/vel && cp tools/parse-check-pom.xml /tmp/vel/pom.xml
    (cd /tmp/vel && mvn -q dependency:copy-dependencies -DoutputDirectory=lib)
    javac -cp "/tmp/vel/lib/*" -d /tmp/vel tools/ParseCheck.java
    java -cp "/tmp/vel:/tmp/vel/lib/*" ParseCheck $(find src -name '*.xml')

## Backup page browser test

    mkdir -p /tmp/sbtest && python3 tools/make-backup-harness.py /tmp/sbtest
    node tools/backup-ui-test.js /tmp/sbtest

Runs the script of the Sensitivity Backup admin section in Chromium against a mocked server. It checks chunked reading, the backup file, the dry run gate before any restore, and the confirmation dialogs. It does not test the Velocity handlers.

## Migration page browser test

    mkdir -p /tmp/sbtest && python3 tools/make-migrate-harness.py /tmp/sbtest
    node tools/migrate-ui-test.js /tmp/sbtest

Tests the parser, the dry run gate and the two step flow of the Sensitivity Migration section against a mock of its handler.

## Handler logic test

Runs the Velocity of the migration, restore, backup and save handlers under the Velocity 2.3 engine against stand-ins for XWiki pages, once with null assignment allowed and once without. It checks access control, counts, which pages are written, what is written, and that a page without a sensitivity object never inherits the previous page's values.

    cd tools/handler-test && mvn -q dependency:copy-dependencies -DoutputDirectory=lib && javac -cp "lib/*" -d . HandlerHarness.java
    cd ../.. && R=src/main/resources/SensitivityBanner/Code
    java -cp "tools/handler-test:tools/handler-test/lib/*" HandlerHarness $R/MigrateHandler.xml $R/RestoreChunk.xml $R/BackupChunk.xml $R/Common.xml $R/Save.xml

The stand-ins are not XWiki. Query syntax, real rights checks and real page saves are not covered.
