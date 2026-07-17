import re
t = open(r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt", "r", encoding="utf-8").read()

sections = ["仓库配置", "日记配置", "附件配置", "图片设置", "时间戳设置", "编辑器设置", "小部件设置"]
for s in sections:
    idx = t.find(s)
    if idx >= 0:
        ctx = t[max(0,idx-30):idx+50].replace("\n", "\\n")
        print(f"[OK] {s} at {idx}: ...{ctx}...")
    else:
        print(f"[MISS] {s}")

# Check version
print()
v = open(r"C:\Users\Ivan\Documents\QuickDaily\app\build.gradle.kts", "r", encoding="utf-8").read()
vm = re.search(r'versionName = "([^"]+)"', v)
if vm:
    print(f"Version: {vm.group(1)}")
