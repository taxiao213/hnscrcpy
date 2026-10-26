#!/bin/bash
# spike-keymap.sh — 真机标定 OpenHarmony uinput 键码表（ASCII 快通道用）v2
#
# 原理：空笔记正文（RichEditor）聚焦后逐键码注入按键，每次注入后读正文文本，
#       长度 +1 的尾字符即该键码产出（对时钟等无关 UI 变化免疫）。
#       末尾另验证单命令多段 -d/-u（Shift 组合键）是否可用。
#
# 前置准备（重要）：
#   1. 手机已连接（hdc list targets 可见），屏幕常亮
#   2. 新建一条空备忘录，点击正文让光标出现（脚本也支持已在编辑中的笔记，
#      但正文末尾必须是光标落点，且建议为空避免干扰）
#   3. 标定期间不要触碰手机；TAB(2049)/方向键可能移动焦点或光标，
#      脚本每 10 键自动重击正文（位置由正文实时 bounds 推导）把光标拉回文末
#
# 用法: ./scripts/spike-keymap.sh [serial]
# 产物: /tmp/hnscrcpy-keymap/keymap-result.txt —— 把内容贴回来用于回填 KeyCharMap
set -u
HDC="$HOME/.hnscrcpy/tools/mac-arm64/hdc"
SN="${1:-$("$HDC" list targets | head -1)}"
[ -z "$SN" ] || [ "$SN" = "[Empty]" ] && { echo "无设备连接"; exit 1; }

WORK=/tmp/hnscrcpy-keymap
mkdir -p "$WORK"
RESULT="$WORK/keymap-result.txt"
: > "$RESULT"

RANGE_START=${RANGE_START:-2000}
RANGE_END=${RANGE_END:-2100}
# 光标复位点击点：固定坐标会误触键盘上方工具栏（实测 y=1700 点开「小艺帮写」面板），
# 改为每次从正文 RichEditor 实时 bounds 推导（右缘内侧、垂直 40% 处，低于文本行光标落文末）

# 助手脚本落盘（内联 python -c 的单引号嵌套会被 shell 搅坏，独立文件 + -I 隔离）
cat > "$WORK/uibody.py" <<'PYEOF'
"""提取面积最大的 RichEditor 文本（笔记正文）。"""
import json, re, sys

def area(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    return (int(m.group(3)) - int(m.group(1))) * (int(m.group(4)) - int(m.group(2))) if m else 0

raw = open(sys.argv[1], encoding="utf-8", errors="replace").read()
data = json.loads(raw[raw.find("{"):])
best, best_area = "", 0

def walk(n):
    global best, best_area
    a = n.get("attributes") or {}
    if a.get("type") == "RichEditor":
        ar = area(a.get("bounds", ""))
        if ar > best_area:
            best_area, best = ar, a.get("text") or ""
    for c in (n.get("children") or []):
        walk(c)

walk(data)
sys.stdout.write(best)
PYEOF

cat > "$WORK/uipoint.py" <<'PYEOF'
"""输出最大 RichEditor 的光标复位点击坐标 "x y"（右缘内缩、垂直 40%）。"""
import json, re, sys

def parse(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    return tuple(int(g) for g in m.groups()) if m else None

raw = open(sys.argv[1], encoding="utf-8", errors="replace").read()
data = json.loads(raw[raw.find("{"):])
best, best_area = None, 0

def walk(n):
    global best, best_area
    a = n.get("attributes") or {}
    if a.get("type") == "RichEditor":
        b = parse(a.get("bounds", ""))
        if b:
            ar = (b[2] - b[0]) * (b[3] - b[1])
            if ar > best_area:
                best_area, best = ar, b
    for c in (n.get("children") or []):
        walk(c)

walk(data)
if best:
    print(best[2] - 172, best[1] + (best[3] - best[1]) * 2 // 5)
PYEOF

cat > "$WORK/uigrow.py" <<'PYEOF'
"""长度+1 → 输出新字符；缩短 → \x02(DEL)；无变化 → 空；其他 → \x01(WARN)。"""
import sys
before = open(sys.argv[1], encoding="utf-8", errors="replace").read()
after = open(sys.argv[2], encoding="utf-8", errors="replace").read()
if len(after) == len(before) + 1 and after.startswith(before):
    sys.stdout.write(after[-1])
elif len(after) < len(before):
    sys.stdout.write("\x02")
elif after != before:
    sys.stdout.write("\x01")
PYEOF

snapshot_body() {
  "$HDC" -t "$SN" shell "uitest dumpLayout -p /data/local/tmp/kl.json >/dev/null 2>&1; cat /data/local/tmp/kl.json" \
    > "$WORK/dump.json" && python3 -I "$WORK/uibody.py" "$WORK/dump.json"
}

press() { "$HDC" -t "$SN" shell "uinput -K -d $1 -u $1" >/dev/null 2>&1; }

record() { # <code> <char>
  local ch="$1"
  case "$ch" in
    "")      printf "%d\t\n" "$2" >> "$RESULT" ;;
    $'\x02') printf "%d\t<DEL>\n" "$2" >> "$RESULT" ;;
    $'\x01') printf "%d\t<WARN>\n" "$2" >> "$RESULT" ;;
    *)       printf "%d\t%s\n" "$2" "$ch" >> "$RESULT" ;;
  esac
}

echo "== 设备: $SN =="
echo "!! 请确认已在手机上聚焦一个空笔记正文。5 秒后开始标定 $RANGE_START..$RANGE_END ..."
sleep 5

snapshot_body > "$WORK/prev_body.txt"
echo "# 键码标定结果（$(date '+%F %T')，设备 ${SN}）" >> "$RESULT"
echo "# 格式: 键码<TAB>产出字符（空=无输出，<DEL>=删除，<WARN>=非追加变化）" >> "$RESULT"

i=0
for ((code=RANGE_START; code<=RANGE_END; code++)); do
  press "$code"
  sleep 0.45  # 实测键事件→文本落盘延迟偶发 >0.3s，留足余量
  snapshot_body > "$WORK/cur_body.txt"
  ch=$(python3 -I "$WORK/uigrow.py" "$WORK/prev_body.txt" "$WORK/cur_body.txt")
  record "$ch" "$code"
  cp "$WORK/cur_body.txt" "$WORK/prev_body.txt"
  i=$((i+1))
  if [ $((i % 10)) -eq 0 ]; then
    read cx cy < <(python3 -I "$WORK/uipoint.py" "$WORK/dump.json")
    [ -n "${cx:-}" ] && "$HDC" -t "$SN" shell "uitest uiInput click $cx $cy" >/dev/null 2>&1  # 光标拉回文末
    sleep 0.3
    snapshot_body > "$WORK/prev_body.txt"  # 点击后重新取基线（防点击本身改动文本）
  fi
  printf "\r扫描中 %d/%d ..." "$code" "$RANGE_END"
done
echo

echo "--- 组合键验证（Shift+A 应产出大写 A）---"
"$HDC" -t "$SN" shell "uinput -K -d 2047 -d 2017 -u 2017 -u 2047" >/dev/null 2>&1
sleep 0.5
snapshot_body > "$WORK/cur_body.txt"
ch=$(python3 -I "$WORK/uigrow.py" "$WORK/prev_body.txt" "$WORK/cur_body.txt")
if [ "$ch" = "A" ]; then
  echo "COMBINED_SEQ	OK" >> "$RESULT"
  echo "组合键可用：Shift 组合命令可发（大写/符号可走快通道）"
else
  printf "COMBINED_SEQ\tFAIL(产出='%s')\n" "$ch" >> "$RESULT"
  echo "组合键不可用：需 Shift 的字符继续走 inputText"
fi

echo "== 完成，结果在 $RESULT =="
echo "把该文件内容贴回会话，用于回填/修正 KeyCharMap 键码表。"
