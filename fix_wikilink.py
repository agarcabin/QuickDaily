# === Fix 1: Update MdRenderer to handle wikilink alt text ===
fp1 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\markdown\MdRenderer.kt"
with open(fp1, "r", encoding="utf-8") as f:
    c1 = f.read()

old_regex = 'private val IMAGE_WIKI_RE = Regex("""^!\\[\\[([^\\]]+)\\]\\]\\s*$""")'
# The regex is: ^!\[\[([^\]]+)\]\]\s*$
# We need to change it to support |alt: ^!\[\[([^\]|]+)(?:\|([^\]]*))?\]\]\s*$
new_regex = 'private val IMAGE_WIKI_RE = Regex("""^!\\[\\[([^\\]|]+)(?:\\|([^\\]]*))?\\]\\]\\s*$""")'

c1 = c1.replace(old_regex, new_regex)

# Also update the parser to use alt text from group 2
old_parse = """            // ![[filename]] wikilink
            trimmed.matches(IMAGE_WIKI_RE) -> {
                val match = IMAGE_WIKI_RE.find(trimmed)!!
                result.add(MdLine.Image(match.groupValues[1], ""))
            }"""

new_parse = """            // ![[filename]] wikilink
            trimmed.matches(IMAGE_WIKI_RE) -> {
                val match = IMAGE_WIKI_RE.find(trimmed)!!
                val path = match.groupValues[1]
                val alt = match.groupValues.getOrElse(2) { "" }
                result.add(MdLine.Image(path, alt))
            }"""

c1 = c1.replace(old_parse, new_parse)

with open(fp1, "w", encoding="utf-8") as f:
    f.write(c1)
print("MdRenderer updated")

# === Fix 2: Update linkOptions in SettingsScreen ===
fp2 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp2, "r", encoding="utf-8") as f:
    c2 = f.read()

old_links = '"described" to "Markdown ![](描述)",\n    "obsidian_wikilink" to "Obsidian ![[双向链接]]"'
new_links = '"described" to "Markdown：![image_name](路径)",\n    "obsidian_wikilink" to "Obsidian：![[image_name]]"'

if old_links in c2:
    c2 = c2.replace(old_links, new_links)
    print("Link options updated")
else:
    print("Link options NOT FOUND")
    # Debug: search for linkOptions
    import re
    m = re.search(r'linkOptions.*\]', c2, re.DOTALL)
    if m:
        print("Found:", repr(m.group()[:100]))

with open(fp2, "w", encoding="utf-8") as f:
    f.write(c2)

# === Fix 3: Version bump ===
fp3 = r"C:\Users\Ivan\Documents\QuickDaily\app\build.gradle.kts"
with open(fp3, "r", encoding="utf-8") as f:
    c3 = f.read()

c3 = c3.replace('versionCode = 37', 'versionCode = 38')
c3 = c3.replace('versionName = "1.5.10-beta"', 'versionName = "1.5.11-beta"')

with open(fp3, "w", encoding="utf-8") as f:
    f.write(c3)
print("Version bumped in build.gradle.kts")

# === Fix 4: version.json ===
import json
fp4 = r"C:\Users\Ivan\Documents\QuickDaily\version.json"
with open(fp4, "r", encoding="utf-8-sig") as f:
    v = json.load(f)
v["version"] = "1.5.11-beta"
v["body"] = "QuickDaily 1.5.11-beta\n- 首页渲染支持 ![[wikilink]] 格式图片\n- 附件链接格式标签更新"
with open(fp4, "w", encoding="utf-8") as f:
    json.dump(v, f, ensure_ascii=False, indent=4)
print("version.json updated")
