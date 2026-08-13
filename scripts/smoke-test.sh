#!/usr/bin/env sh
set -eu
: "${HAOBLOG_BASE_URL:=http://localhost:3000}"
export HAOBLOG_BASE_URL
node "$(dirname "$0")/smoke-test.mjs"
