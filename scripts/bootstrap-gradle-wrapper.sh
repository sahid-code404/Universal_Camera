#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DEST="$ROOT/gradle/wrapper/gradle-wrapper.jar"
URL="https://services.gradle.org/distributions/gradle-9.5.1-wrapper.jar"
EXPECTED="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
mkdir -p "$(dirname "$DEST")"
TMP="$DEST.tmp"
printf '%s\n' "Bootstrapping Gradle wrapper 9.5.1..."
if command -v curl >/dev/null 2>&1; then
  curl --fail --location --retry 3 --output "$TMP" "$URL"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$TMP" "$URL"
else
  echo "curl or wget is required to bootstrap the Gradle wrapper." >&2
  exit 1
fi
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL="$(shasum -a 256 "$TMP" | awk '{print $1}')"
else
  echo "sha256sum or shasum is required to verify the wrapper." >&2
  rm -f "$TMP"
  exit 1
fi
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Gradle wrapper checksum mismatch." >&2
  echo "Expected: $EXPECTED" >&2
  echo "Actual:   $ACTUAL" >&2
  rm -f "$TMP"
  exit 1
fi
mv "$TMP" "$DEST"
printf '%s\n' "Gradle wrapper verified and installed."
