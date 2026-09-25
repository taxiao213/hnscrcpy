#!/usr/bin/env bash
# 构建当前平台的 jpackage 产物，产物在 target/dist/。
# 默认只生成可直接运行的 app-image（免安装）；INSTALLER=1 时额外生成安装包（dmg/exe）。
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
  Darwin/arm64|Darwin/x86_64) INSTALLER_TYPE=dmg ;;
  Linux/*) INSTALLER_TYPE=deb ;; # 需 fakeroot/dpkg（CI ubuntu-latest 已装）
  *) echo "Windows 请使用 scripts/build-package.ps1"; exit 1 ;;
esac

rm -rf target/dist

# 1) app-image：免安装直接运行（macOS 为 hnscrcpy.app，Linux 为 hnscrcpy/）
jpackage \
  --type app-image \
  --name "$APP" \
  --app-version "$APP_VER" \
  --input target/libs \
  --main-jar "$(basename "$JAR")" \
  --main-class com.hnscrcpy.Launcher \
  --java-options "-Xmx1G" \
  --dest target/dist

# 2) 安装包（仅 INSTALLER=1；macOS=dmg，Linux=deb）
if [ "${INSTALLER:-0}" = "1" ]; then
  EXTRA=()
  if [ "$INSTALLER_TYPE" = "deb" ]; then
    EXTRA=(--linux-package-name "$APP")
  fi
  jpackage \
    --type "$INSTALLER_TYPE" \
    --name "$APP" \
    --app-version "$APP_VER" \
    --input target/libs \
    --main-jar "$(basename "$JAR")" \
    --main-class com.hnscrcpy.Launcher \
    --java-options "-Xmx1G" \
    "${EXTRA[@]}" \
    --dest target/dist
fi

echo "==> 产物: target/dist/"
ls -lh target/dist/
