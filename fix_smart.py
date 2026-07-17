fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\util\ImageUtil.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

old = """    if (name != null && name.contains(".")) {
        return "." + name.substringAfterLast(".")
    }"""
new = """    val n = name
    if (n != null && n.contains(".")) {
        return "." + n.substringAfterLast(".")
    }"""

if old in c:
    c = c.replace(old, new)
    with open(fp, "w", encoding="utf-8") as f:
        f.write(c)
    print("Fixed smart cast")
else:
    print("Pattern not found")
    # Debug: show what's around that area
    import re
    m = re.search(r"if \(name != null", c)
    if m:
        start = max(0, m.start() - 5)
        end = min(len(c), m.end() + 50)
        print("Found near:", repr(c[start:end]))
import re
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\util\ImageUtil.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()
# Replace the smart cast issue lines
old = '        if (name != null && name.contains(".")) {\n            return "." + name.substringAfterLast(".")\n        }'
new = '        val n = name\n        if (n != null && n.contains(".")) {\n            return "." + n.substringAfterLast(".")\n        }'
if old in c:
    c = c.replace(old, new)
    with open(fp, "w", encoding="utf-8") as f:
        f.write(c)
    print("Fixed")
else:
    print("Not found - checking lines")
    lines = c.split("\n")
    for i, l in enumerate(lines):
        if 'name != null' in l:
            print(f"Line {i+1}: {repr(l)}")
