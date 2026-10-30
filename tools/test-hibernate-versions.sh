#!/usr/bin/env bash
# Runs the test suite against several Hibernate 7 versions (latest patch of each minor by default).
# Usage: tools/test-hibernate-versions.sh [VERSION ...]
set -u
cd "$(dirname "$0")/.."

versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
    versions=(7.0.10.Final 7.1.36.Final 7.2.25.Final 7.3.13.Final 7.4.0.Final 7.4.14.Final)
fi

status=0
for version in "${versions[@]}"; do
    if mvn -q test -Dhibernate.version="$version" > /dev/null 2>&1; then
        echo "PASS  $version"
    else
        echo "FAIL  $version   (rerun: mvn test -Dhibernate.version=$version)"
        status=1
    fi
done
exit $status
