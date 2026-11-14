# Tools

Run both before releasing. Neither needs a running XWiki.

## Class contract check

    python3 tools/check-class-contract.py

Fails if the stored data fields of `SensitivityBanner.Code.SensitivityClass` (`owner`, `security`, `country`) were renamed or retyped. Values already saved on pages are stored under those names.

## Velocity syntax check

Parses every `{{velocity}}` block in the page XML with Velocity 2.3. It catches syntax errors such as a method call on a string literal. It does not prove the pages behave correctly on a wiki.

    mkdir -p /tmp/vel && cp tools/parse-check-pom.xml /tmp/vel/pom.xml
    (cd /tmp/vel && mvn -q dependency:copy-dependencies -DoutputDirectory=lib)
    javac -cp "/tmp/vel/lib/*" -d /tmp/vel tools/ParseCheck.java
    java -cp "/tmp/vel:/tmp/vel/lib/*" ParseCheck $(find src -name '*.xml')
