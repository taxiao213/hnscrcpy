#!/bin/bash
# spike-text-inject.sh — 真机标定文本注入通道（连上手机后执行）
# 用法: ./scripts/spike-text-inject.sh [serial]
# 验证: ① uitest uiInput inputText 是否存在 ② ASCII/中文注入效果
#       ③ 注入是否重点击坐标 ④ uinput 键值（ESC/方向键）
set -u
HDC="$HOME/.hnscrcpy/tools/mac-arm64/hdc"
SN="${1:-$("$HDC" list targets | head -1)}"
[ -z "$SN" ] || [ "$SN" = "[Empty]" ] && { echo "无设备连接"; exit 1; }
echo "== 设备: $SN =="

echo "--- 1) uitest uiInput 子命令列表 ---"
"$HDC" -t "$SN" shell uitest uiInput 2>&1 | head -20

echo "--- 2) inputText ASCII（请在设备上打开任意输入框后 3 秒内观察）---"
sleep 3
"$HDC" -t "$SN" shell uitest uiInput inputText 600 2000 'hello123' && echo "[ok] ASCII"

echo "--- 3) inputText 中文 ---"
"$HDC" -t "$SN" shell uitest uiInput inputText 600 2000 '你好世界' && echo "[ok] 中文"

echo "--- 4) uinput 键值标定（观察设备反应: ESC=2070, 上=2071, 下=2072, 左=2073, 右=2074）---"
for k in 2070 2071 2072 2073 2074; do
  echo "键 $k:"; "$HDC" -t "$SN" shell "uinput -K -d $k -u $k"; sleep 1
done

echo "--- 完成。把结论回填 docs/HOS_SCRCPY_PROTOCOL.md §10 与 DeviceController 键值表 ---"
