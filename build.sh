#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then echo 'Please set ANDROID_HOME or ANDROID_SDK_ROOT.' >&2; exit 1; fi
build_tools="$sdk_root/build-tools/35.0.0"
android_jar="$sdk_root/platforms/android-35/android.jar"
mkdir -p build/classes build/dex build/generated
"$build_tools/aapt" package -f -M AndroidManifest.xml -S res -I "$android_jar" -J build/generated -F build/base.apk
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$android_jar" -d build/classes build/generated/R.java src/cn/miniclock/*.java
"$build_tools/d8" --release --min-api 24 --lib "$android_jar" --output build/dex build/classes/cn/miniclock/*.class
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$build_tools/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
keystore="${SIGNING_KEYSTORE:-build/development.p12}"
store_password="${SIGNING_STORE_PASSWORD:-android}"
key_alias="${SIGNING_KEY_ALIAS:-clock}"
if [[ ! -f "$keystore" ]]; then
  if [[ -n "${SIGNING_KEYSTORE:-}" ]]; then echo 'Signing keystore not found.' >&2; exit 1; fi
  keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -storepass "$store_password" -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Mini Clock Development'
fi
export CLOCK_STORE_PASSWORD="$store_password"
"$build_tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" --ks-pass env:CLOCK_STORE_PASSWORD --out build/MillisecondClock.apk build/aligned.apk
"$build_tools/apksigner" verify --verbose build/MillisecondClock.apk
