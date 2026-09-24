# 构建 Windows 产物，产物在 target/dist/。
# 默认只生成可直接运行的 app-image（hnscrcpy/hnscrcpy.exe，免安装）；
# 设置环境变量 INSTALLER=1 时额外生成安装包（exe）。
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

# 1) app-image：免安装直接运行 target/dist/hnscrcpy/hnscrcpy.exe
jpackage `
  --type app-image `
  --name $APP `
  --app-version $APP_VER `
  --input target/libs `
  --main-jar (Split-Path $JAR -Leaf) `
  --main-class com.hnscrcpy.Launcher `
  --java-options "-Xmx1G" `
  --dest target/dist

# 2) 安装包（仅 INSTALLER=1）
if ($env:INSTALLER -eq "1") {
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
}

Write-Output "==> 产物: target/dist/"
Get-ChildItem target/dist
