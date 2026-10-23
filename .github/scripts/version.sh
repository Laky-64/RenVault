#!/usr/bin/env bash
set -euo pipefail

base=$(sed -n 's/^renvault\.version=//p' gradle.properties | tr -d '\r')
if [ "${GITHUB_REF_TYPE:-}" = "tag" ]; then
  name="${GITHUB_REF_NAME#v}"
else
  name="$base-dev.$GITHUB_RUN_NUMBER"
fi

IFS=. read -r major minor _ <<< "${name%%-*}"

{
  echo "name=$name"
  echo "code=$((1000 + GITHUB_RUN_NUMBER))"
  echo "package=$major.$minor.$GITHUB_RUN_NUMBER"
} >> "$GITHUB_OUTPUT"
