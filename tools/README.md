# Tools

Run both before releasing. Neither needs a running XWiki.

## Building without Maven

    python3 tools/build-xar.py [MODULE_DIR] [OUTPUT.xar]

Builds a XAR from `MODULE_DIR/src/main/resources` using only the Python standard library, with the name, description and version taken from that module's `pom.xml`. The repository holds two modules: the Sensitivity Banner at the repository root (the default) and the temporary Sensitivity Migration tool in `migration`. Run `python3 tools/build-xar.py migration` for the second. A XAR is a zip of the page XML files plus a `package.xml` that lists them. The output matches the Maven build, page for page, and is reproducible.

## Class contract check

    python3 tools/check-class-contract.py

Fails if the stored data fields of `SensitivityBanner.Code.SensitivityClass` (`owner`, `security`, `country`) were renamed or retyped. Values already saved on pages are stored under those names. It also checks the contract between the two extensions: the migration tool may only use that class, the page `SensitivityBanner.Code.Common` and the variables and macros that page defines, and the banner must never mention the migration tool.

## Velocity syntax check

Parses every `{{velocity}}` block in the page XML with Velocity 2.3. It catches syntax errors such as a method call on a string literal. It does not prove the pages behave correctly on a wiki.

    mkdir -p /tmp/vel && cp tools/parse-check-pom.xml /tmp/vel/pom.xml
    (cd /tmp/vel && mvn -q dependency:copy-dependencies -DoutputDirectory=lib)
    javac -cp "/tmp/vel/lib/*" -d /tmp/vel tools/ParseCheck.java
    java -cp "/tmp/vel:/tmp/vel/lib/*" ParseCheck $(find src migration -name '*.xml')

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
    java -cp "tools/handler-test:tools/handler-test/lib/*" HandlerHarness migration/src/main/resources/SensitivityMigration/Code/Handler.xml $R/RestoreChunk.xml $R/BackupChunk.xml $R/Common.xml $R/Save.xml

There is also `SheetGuardTest.java`, which runs the migration screen's Velocity with the banner present and absent and checks that the error appears and the buttons do not when it is missing:

    javac -cp "tools/handler-test/lib/*" -d tools/handler-test tools/handler-test/SheetGuardTest.java
    java -cp "tools/handler-test:tools/handler-test/lib/*" SheetGuardTest migration/src/main/resources/SensitivityMigration/Code/Sheet.xml

The stand-ins are not XWiki. Query syntax, real rights checks and real page saves are not covered.
