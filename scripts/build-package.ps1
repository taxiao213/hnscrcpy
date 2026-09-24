# 构建 Windows 安装包（exe），产物在 target/dist/。
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$APP = "hnscrcpy"
$VER = (mvn -q help:evaluate -Dexpression=project.version -DforceStdout)
$APP_VER = $VER -replace "-SNAPSHOT", ""
$JAR = "target/$APP-$VER.jar"

mvn -q package -DskipTests
Remove-Item -Recurse -Force target/libs -ErrorAction SilentlyContinue
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/libs
Copy-Item $JAR target/libs/

Remove-Item -Recurse -Force target/dist -ErrorAction SilentlyContinue
jpackage `
  --type exe `
  --name $APP `
  --app-version $APP_VER `
  --input target/libs `
  --main-jar (Split-Path $JAR -Leaf) `
  --main-class com.hnscrcpy.Launcher `
  --java-options "-Xmx1G" `
  --win-menu `
  --win-shortcut `
  --dest target/dist

Write-Output "==> 产物: target/dist/"
Get-ChildItem target/dist
