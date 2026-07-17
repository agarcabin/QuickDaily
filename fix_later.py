# Fix 1: Remove backslash before $ in token/desc string templates
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

# Fix the escaped dollar signs
c = c.replace('"\$token - \$desc"', '"$token - $desc"')
c = c.replace('"已复制 \$token"', '"已复制 $token"')

# Add Refresh icon import
old_imports = 'import androidx.compose.material.icons.filled.TextFormat'
new_imports = old_imports + '\nimport androidx.compose.material.icons.filled.Refresh'
c = c.replace(old_imports, new_imports)

# Fix 2: Add trailingIcon (Refresh) to custom naming format field
old_tf = '''                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )'''

new_tf = '''                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onConfigChange(config.copy(imageCustomNamingFormat = "yyyy-MM-dd_HHmmss_{filename}{ext}")) }) {
                                Icon(Icons.Default.Refresh, "重置为默认格式")
                            }
                        }
                    )'''

c = c.replace(old_tf, new_tf)

with open(fp, "w", encoding="utf-8") as f:
    f.write(c)
print("Fixes applied")
# Fix EditorScreen non-image attachment link format
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\EditorScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

old = '''                val relativePath = if (dir.isNotEmpty()) dir + "/" + fileName else fileName
                "[" + displayName + ext + "](" + relativePath + ")"
            }'''

new = '''                val relativePath = if (dir.isNotEmpty()) dir + "/" + fileName else fileName
                when (config.imageLinkFormat) {
                    "obsidian_wikilink" -> "![[" + relativePath + "]]"
                    else -> "[" + displayName + ext + "](" + relativePath + ")"
                }
            }'''

if old in c:
    c = c.replace(old, new)
    with open(fp, "w", encoding="utf-8") as f:
        f.write(c)
    print("Non-image attachment link format fixed")
else:
    print("NOT FOUND - showing context")
    import re
    m = re.search(r'relativePath.*fileName', c)
    if m:
        start = max(0, m.start()-5)
        end = min(len(c), m.end()+60)
        print(repr(c[start:end]))
# Simplify: always use Obsidian wikilink for non-image attachments
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\EditorScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

old = '''                when (config.imageLinkFormat) {
                    "obsidian_wikilink" -> "![[" + relativePath + "]]"
                    else -> "[" + displayName + ext + "](" + relativePath + ")"
                }'''

new = '''                "![[" + relativePath + "]]"'''

if old in c:
    c = c.replace(old, new)
    with open(fp, "w", encoding="utf-8") as f:
        f.write(c)
    print("Changed to always use wikilink")
else:
    print("Pattern not found")
    import re
    for m in re.finditer(r'when.*imageLinkFormat.*obsidian_wikilink', c, re.DOTALL):
        start = max(0, m.start()-20)
        end = min(len(c), m.end()+100)
        print("Found:", repr(c[start:end])[:200])
