#!/usr/bin/env bash
# Dựng StandBy.apk không cần Gradle: aapt2 → javac → d8 → zipalign → apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-35/android.jar
VER_CODE=${VER_CODE:-1}
VER_NAME=${VER_NAME:-1.0}

rm -rf build && mkdir -p build/classes build/dex build/gen
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o build/base.apk build/res.zip \
  --java build/gen --min-sdk-version 31 --target-sdk-version 35 \
  --version-code "$VER_CODE" --version-name "$VER_NAME"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -cp "$JAR" -Xlint:-options -nowarn \
  -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --min-api 31 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/app.apk
(cd build/dex && zip -q ../app.apk classes.dex)
"$BT/zipalign" -f -p 4 build/app.apk build/aligned.apk
[ -f keystore.jks ] || keytool -genkeypair -keystore keystore.jks -storepass standbykey -keypass standbykey \
  -alias standby -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=StandBy" >/dev/null 2>&1
"$BT/apksigner" sign --ks keystore.jks --ks-pass pass:standbykey --out StandBy.apk build/aligned.apk
echo "OK → $(pwd)/StandBy.apk"
