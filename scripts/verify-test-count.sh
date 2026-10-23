#!/usr/bin/env bash
# Fails unless every @Test written in the sources was actually executed.
#
# A green Gradle build does not prove that tests ran: a misconfigured test
# engine or a stray filter can run zero tests and still report success.
# Comparing the number of executed tests with the number of @Test
# annotations catches that. Run it after `./gradlew check`.
#
# Only the engine module has tests for now; extend both paths when the
# Android app gets its own.
set -euo pipefail

sources_dir="engine/src/test"
results_dir="engine/build/test-results/test"

declared=$(grep -rhE --include='*.kt' '^[[:space:]]*@Test[[:space:]]*$' "$sources_dir" | wc -l | tr -d ' ')

shopt -s nullglob
reports=("$results_dir"/*.xml)
if (( ${#reports[@]} == 0 )); then
    echo "::error::No test report found in $results_dir: did the tests run at all?"
    exit 1
fi

executed=0
for report in "${reports[@]}"; do
    header=$(grep -o '<testsuite [^>]*' "$report")
    tests=$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$header")
    skipped=$(sed -E 's/.* skipped="([0-9]+)".*/\1/' <<<"$header")
    executed=$((executed + tests - skipped))
done

echo "Tests declared: $declared, executed: $executed"
if (( executed != declared )); then
    echo "::error::$declared tests are declared but $executed were executed."
    exit 1
fi
