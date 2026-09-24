#!/usr/bin/env bash
# 构建当前平台的 jpackage 安装包，产物在 target/dist/。
set -euo pipefail
cd "$(dirname "$0")/.."

APP=hnscrcpy
VER=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)
APP_VER=${VER//-SNAPSHOT/}
JAR="target/$APP-$VER.jar"

mvn -q package -DskipTests
rm -rf target/libs
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/libs
cp "$JAR" target/libs/

OS=$(uname -s)
ARCH=$(uname -m)
case "$OS/$ARCH" in
  Darwin/arm64|Darwin/x86_64) TYPE=dmg ;;
  Linux/*) TYPE=app-image ;;
  *) echo "Windows 请使用 scripts/build-package.ps1"; exit 1 ;;
esac

rm -rf target/dist
jpackage \
  --type "$TYPE" \
  --name "$APP" \
  --app-version "$APP_VER" \
  --input target/libs \
  --main-jar "$(basename "$JAR")" \
  --main-class com.hnscrcpy.Launcher \
  --java-options "-Xmx1G" \
  --dest target/dist

echo "==> 产物: target/dist/"
ls -lh target/dist/
