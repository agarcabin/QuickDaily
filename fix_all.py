# === 1. DiaryConfig: Set default custom naming format ===
fp1 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\AppState.kt"
with open(fp1, "r", encoding="utf-8") as f:
    c1 = f.read()

# Change the default in data class
c1 = c1.replace(
    'val imageCustomNamingFormat: String = ""',
    'val imageCustomNamingFormat: String = "yyyy-MM-dd_HHmmss_{filename}{ext}"'
)

# Change the default in loadConfig()
c1 = c1.replace(
    'imageCustomNamingFormat = prefs.getString("image_custom_naming_format", "") ?: ""',
    'imageCustomNamingFormat = prefs.getString("image_custom_naming_format", "yyyy-MM-dd_HHmmss_{filename}{ext}") ?: "yyyy-MM-dd_HHmmss_{filename}{ext}"'
)

with open(fp1, "w", encoding="utf-8") as f:
    f.write(c1)
print("DiaryConfig default updated")

# === 2. SettingsScreen: Clickable placeholder tokens ===
fp2 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp2, "r", encoding="utf-8") as f:
    c2 = f.read()

# Add import for LocalClipboardManager
c2 = c2.replace(
    "import androidx.compose.ui.platform.LocalContext",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalClipboardManager"
)

# Add import for clickable
c2 = c2.replace(
    "import androidx.compose.foundation.combinedClickable",
    "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable"
)

# Replace the old help text block with clickable version
old_help = """                    Spacer(Modifier.height(8.dp))
                    Text(\"可用占位符：\", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(\"{filename} - 原文件名（不含扩展名）\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"{ext} - 扩展名（如 .jpg）\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"yyyy - 四位年份\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"MM - 两位月份\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"dd - 两位日期\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"HH - 24小时制（00-23）\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"mm - 分钟（00-59）\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text(\"ss - 秒钟（00-59）\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))"""

new_help = """                    Spacer(Modifier.height(8.dp))
                    Text(\"可用占位符（点击可复制）：\", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    val clipboard = LocalClipboardManager.current
                    val ctx = LocalContext.current
                    val entries = listOf(
                        \"{filename}\" to \"原文件名（不含扩展名）\",
                        \"{ext}\" to \"扩展名（如 .jpg）\",
                        \"yyyy\" to \"四位年份\",
                        \"MM\" to \"两位月份\",
                        \"dd\" to \"两位日期\",
                        \"HH\" to \"24小时制（00-23）\",
                        \"mm\" to \"分钟（00-59）\",
                        \"ss\" to \"秒钟（00-59）\",
                    )
                    entries.forEach { (token, desc) ->
                        Text(
                            text = \"\$token - \$desc\",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable {
                                    clipboard.setText(AnnotatedString(token))
                                    android.widget.Toast.makeText(ctx, \"已复制 \$token\", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        )
                    }"""

if old_help in c2:
    c2 = c2.replace(old_help, new_help)
    print("Settings help text updated")
else:
    print("Settings help text NOT FOUND")
    # Debug
    import re
    m = re.search(r"可用占位符", c2)
    if m:
        start = max(0, m.start()-20)
        end = min(len(c2), m.end()+300)
        print("Found near:", repr(c2[start:end])[:500])

with open(fp2, "w", encoding="utf-8") as f:
    f.write(c2)

# === 3. MdRenderer: Add imageStoragePath fallback ===
fp3 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\markdown\MdRenderer.kt"
with open(fp3, "r", encoding="utf-8") as f:
    c3 = f.read()

# Update function signature to add imageStoragePath
old_sig = """@Composable
fun MdRenderer(
    text: String,
    vaultBasePath: String? = null,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
)"""

new_sig = """@Composable
fun MdRenderer(
    text: String,
    vaultBasePath: String? = null,
    imageStoragePath: String? = null,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
)"""

c3 = c3.replace(old_sig, new_sig)

# Update the image rendering to try attachment folder as fallback
old_render = """                is MdLine.Image -> {
                    val fullPath = remember(line.path, vaultBasePath) {
                        resolveImagePath(line.path, vaultBasePath)
                    }
                    val bitmap = remember(fullPath) {
                        try {
                            BitmapFactory.decodeFile(fullPath)
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = line.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        BasicText(
                            text = AnnotatedString(line.alt.ifEmpty { \"[图片: ${line.path}]\" }),
                            style = LocalTextStyle.current.copy(color = Color.Gray),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }"""

new_render = """                is MdLine.Image -> {
                    val paths = remember(line.path, vaultBasePath, imageStoragePath) {
                        val primary = resolveImagePath(line.path, vaultBasePath)
                        val fallback = if (imageStoragePath != null && !line.path.startsWith(\"/\")) {
                            resolveImagePath(\"${imageStoragePath.trimEnd(\'/\')}/${line.path.trimStart(\'/\')}\", vaultBasePath)
                        } else null
                        Pair(primary, fallback)
                    }
                    val bitmap = remember(paths) {
                        try {
                            BitmapFactory.decodeFile(paths.first)
                                ?: paths.second?.let { BitmapFactory.decodeFile(it) }
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = line.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        BasicText(
                            text = AnnotatedString(line.alt.ifEmpty { \"[图片: ${line.path}]\" }),
                            style = LocalTextStyle.current.copy(color = Color.Gray),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }"""

c3 = c3.replace(old_render, new_render)

# Update the wikilink parser to use alt text
old_parse = """            // ![[filename]] wikilink
            trimmed.matches(IMAGE_WIKI_RE) -> {
                val match = IMAGE_WIKI_RE.find(trimmed)!!
                result.add(MdLine.Image(match.groupValues[1], \"\"))
            }"""

new_parse = """            // ![[filename]] wikilink
            trimmed.matches(IMAGE_WIKI_RE) -> {
                val match = IMAGE_WIKI_RE.find(trimmed)!!
                val path = match.groupValues[1]
                val alt = match.groupValues.getOrElse(2) { \"\" }
                result.add(MdLine.Image(path, alt))
            }"""

if old_parse in c3:
    c3 = c3.replace(old_parse, new_parse)
    print("MdRenderer wikilink parser updated")
else:
    print("MdRenderer wikilink parser NOT FOUND")
    # Find the actual text
    import re
    m = re.search(r"IMAGE_WIKI_RE.*result.add", c3, re.DOTALL)
    if m:
        print("Found:", repr(m.group()[:200]))

with open(fp3, "w", encoding="utf-8") as f:
    f.write(c3)
print("MdRenderer updated")

# === 4. EditorScreen: Pass imageStoragePath ===
fp4 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\EditorScreen.kt"
with open(fp4, "r", encoding="utf-8") as f:
    c4 = f.read()

old_call = """MdRenderer(text = diaryContent, vaultBasePath = config.vaultPath, onToggleCheckbox = { index ->"""
new_call = """MdRenderer(text = diaryContent, vaultBasePath = config.vaultPath, imageStoragePath = config.imageStoragePath.takeIf { it.isNotBlank() }, onToggleCheckbox = { index ->"""

c4 = c4.replace(old_call, new_call)
print("EditorScreen MdRenderer call updated")

with open(fp4, "w", encoding="utf-8") as f:
    f.write(c4)

print("\\nAll fixes applied!")
# === Fix wikilink parser in MdRenderer ===
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\markdown\MdRenderer.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

old_line = '                result.add(MdLine.Image(match.groupValues[1], ""))'
new_lines = """                val path = match.groupValues[1]
                val alt = match.groupValues.getOrElse(2) { "" }
                result.add(MdLine.Image(path, alt))"""

c = c.replace(old_line, new_lines)

with open(fp, "w", encoding="utf-8") as f:
    f.write(c)

if old_line in c:
    print("Wikilink parser: still has old pattern")
else:
    print("Wikilink parser: fixed!")
