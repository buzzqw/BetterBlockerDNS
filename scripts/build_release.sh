#!/usr/bin/env bash
# Build the F-Droid release package and email it to the owner.
# F-Droid and other external builders call Gradle directly, so they never
# trigger the email hook.
set -euo pipefail

cd "$(dirname "$0")/.."

BBDNS_EMAIL_BUILD=1 exec ./gradlew :app:assembleFdroidRelease "$@"
