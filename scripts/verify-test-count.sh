#!/usr/bin/env bash
# Fails unless every @Test written in the sources was actually executed.
#
# A green Gradle build does not prove that tests ran: a misconfigured test
# engine or a stray filter can run zero tests and still report success.
# Comparing the number of executed tests with the number of @Test
# annotations catches that. Run it after `./gradlew check`.
set -euo pipefail

# Each entry: test sources, then the directory of their JUnit reports. For the
# Android app only the debug variant is counted: `check` runs the unit tests of
# both the debug and the release variants, which would count each test twice.
modules=(
    "engine/src/test engine/build/test-results/test"
    "app/src/test app/build/test-results/testDebugUnitTest"
)

shopt -s nullglob
declared=0
executed=0
for module in "${modules[@]}"; do
    read -r sources_dir results_dir <<<"$module"
    # grep exits with 1 when it finds nothing: a module without tests must count 0, not fail.
    count=$({ grep -rhE --include='*.kt' '^[[:space:]]*@Test[[:space:]]*$' "$sources_dir" || true; } | wc -l | tr -d ' ')
    declared=$((declared + count))
    reports=("$results_dir"/*.xml)
    if (( count > 0 && ${#reports[@]} == 0 )); then
        echo "::error::No test report found in $results_dir: did the tests run at all?"
        exit 1
    fi
    for report in "${reports[@]}"; do
        header=$(grep -o '<testsuite [^>]*' "$report")
        tests=$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$header")
        skipped=$(sed -E 's/.* skipped="([0-9]+)".*/\1/' <<<"$header")
        executed=$((executed + tests - skipped))
    done
done

echo "Tests declared: $declared, executed: $executed"
if (( executed != declared )); then
    echo "::error::$declared tests are declared but $executed were executed."
    exit 1
fi
