import re

# ===== FILE 1: ImageUtil.kt =====
fp1 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\util\ImageUtil.kt"
with open(fp1, "r", encoding="utf-8") as f:
    c1 = f.read()

# 1a. Update timestamp format to include hyphens
c1 = c1.replace('"yyyyMMdd_HHmmss"', '"yyyy-MM-dd_HHmmss"')

# 1b. Fix getExtension to handle non-image file types
old_ext = '''    fun getExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        return when {
            mimeType.contains("png") -> ".png"
            mimeType.contains("gif") -> ".gif"
            mimeType.contains("webp") -> ".webp"
            mimeType.contains("bmp") -> ".bmp"
            else -> ".jpg"
        }
    }'''

new_ext = '''    fun getExtension(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) { name = it.getString(idx) }
                }
            }
        } catch (_: Exception) {}
        if (name != null && name.contains(".")) {
            return "." + name.substringAfterLast(".")
        }
        val mimeType = context.contentResolver.getType(uri) ?: return ".bin"
        return when {
            mimeType.contains("png") -> ".png"
            mimeType.contains("gif") -> ".gif"
            mimeType.contains("webp") -> ".webp"
            mimeType.contains("bmp") -> ".bmp"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
            mimeType.contains("pdf") -> ".pdf"
            mimeType.contains("msword") || mimeType.contains("word") -> ".doc"
            mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("sheet") -> ".xls"
            mimeType.contains("presentation") || mimeType.contains("powerpoint") || mimeType.contains("ppt") -> ".ppt"
            mimeType.contains("text") -> ".txt"
            mimeType.contains("html") -> ".html"
            mimeType.contains("json") -> ".json"
            mimeType.contains("zip") -> ".zip"
            mimeType.contains("rar") -> ".rar"
            mimeType.contains("octet-stream") -> ".bin"
            mimeType.contains("video") -> ".mp4"
            mimeType.contains("audio") -> ".mp3"
            else -> ".bin"
        }
    }'''

c1 = c1.replace(old_ext, new_ext)

with open(fp1, "w", encoding="utf-8") as f:
    f.write(c1)
print("ImageUtil.kt updated")

# ===== FILE 2: SettingsScreen.kt =====
fp2 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp2, "r", encoding="utf-8") as f:
    c2 = f.read()

# 2a. Update namingOptions
old_opts = '''private val namingOptions = listOf(
    NamingOption("timestamp_original", "时间戳_原名"),
    NamingOption("timestamp_ext", "时间戳+扩展名"),
    NamingOption("original", "保留原名"),
    NamingOption("custom", "自定义格式"),
)'''

new_opts = '''private val namingOptions = listOf(
    NamingOption("original", "原名（image.jpg）"),
    NamingOption("timestamp_original", "时间戳+原名（2026-07-17_120820_image.jpg）"),
    NamingOption("custom", "自定义名称"),
)'''

c2 = c2.replace(old_opts, new_opts)

# 2b. Add help text in custom format block
old_custom = '''                if (config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }'''

new_custom = '''                if (config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("可用占位符：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("{filename} - 原文件名（不含扩展名）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("{ext} - 扩展名（如 .jpg）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("yyyy - 四位年份", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("MM - 两位月份", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("dd - 两位日期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("HH - 24小时制（00-23）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("mm - 分钟（00-59）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                    Text("ss - 秒钟（00-59）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                }'''

c2 = c2.replace(old_custom, new_custom)

# 2c. Update storage path example with dynamic preview
old_example = '''                if (vaultPath.isNotBlank() && imageStoragePath.isNotBlank()) {
                    Text(
                        "附件储存路径示例：" + vaultPath + "/" + imageStoragePath + "/image.jpg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }'''

new_example = '''                if (vaultPath.isNotBlank() && imageStoragePath.isNotBlank()) {
                    val previewName = remember(config.imageNamingFormat, config.imageCustomNamingFormat) {
                        com.quickdaily.util.ImageUtil.generateFileName(config.imageNamingFormat, "image", ".jpg", config.imageCustomNamingFormat)
                    }
                    Text(
                        "附件储存路径示例：" + vaultPath + "/" + imageStoragePath + "/" + previewName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }'''

c2 = c2.replace(old_example, new_example)

with open(fp2, "w", encoding="utf-8") as f:
    f.write(c2)
print("SettingsScreen.kt updated")
