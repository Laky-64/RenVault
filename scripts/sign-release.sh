#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

tag=${1:?Usage: scripts/sign-release.sh <tag>}
key=$(gpg --list-secret-keys --with-colons "RenVault Release Signing" | awk -F: '/^fpr/ { print $10; exit }')
[ -n "$key" ] || { echo "RenVault Release Signing key not found" >&2; exit 1; }
repo=$(git remote get-url origin | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

gh release download "$tag" --repo "$repo" --dir "$work" --pattern 'RenVault-*'
(
  cd "$work"
  sha256sum -- RenVault-* > SHA256SUMS
  gpg --local-user "$key" --digest-algo sha256 --armor --detach-sign --output SHA256SUMS.asc SHA256SUMS
  gpg --verify SHA256SUMS.asc SHA256SUMS
)
gh release upload "$tag" "$work/SHA256SUMS" "$work/SHA256SUMS.asc" --repo "$repo" --clobber
echo "Signed $tag"
