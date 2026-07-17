import re
t = open(r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt", "r", encoding="utf-8").read()
sections = re.findall(r'Text\("[^"]*"\)', t)
for s in sections:
    print(s)
print("\n---")
# Check for specific patterns
print("仓库配置:", "仓库配置" in t)
print("日记配置:", "日记配置" in t)
print("附件配置:", "附件配置" in t)
print("图片设置:", "图片设置" in t)
print("小部件设置:", "小部件设置" in t)
