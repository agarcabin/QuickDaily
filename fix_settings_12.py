import re, sys
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    text = f.read()

changes = 0
crlf = '\r\n'

# 1. Add imports if needed
if 'import android.content.ClipData' not in text:
    text = text.replace(
        'import android.content.ContentValues',
        'import android.content.ContentValues\nimport android.content.ClipData\nimport android.content.ClipboardManager'
    )
    changes += 1
    print('1. Added ClipData/ClipboardManager imports')

# 2. Replace the custom naming format section
old = (
    '                if (config.imageNamingFormat == "custom") {' + crlf +
    '                    OutlinedTextField(' + crlf +
    '                        value = config.imageCustomNamingFormat,' + crlf +
    '                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },' + crlf +
    '                        label = { Text("\u81ea\u5b9a\u4e49\u547d\u540d\u683c\u5f0f") },' + crlf +
    '                        modifier = Modifier.fillMaxWidth(),' + crlf +
    '                        singleLine = true' + crlf +
    '                    )' + crlf +
    '                }'
)

new = (
    '                if (config.imageNamingFormat == "custom") {' + crlf +
    '                    OutlinedTextField(' + crlf +
    '                        value = config.imageCustomNamingFormat,' + crlf +
    '                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },' + crlf +
    '                        label = { Text("\u81ea\u5b9a\u4e49\u547d\u540d\u683c\u5f0f") },' + crlf +
    '                        modifier = Modifier.fillMaxWidth(),' + crlf +
    '                        singleLine = true,' + crlf +
    '                        trailingIcon = {' + crlf +
    '                            IconButton(onClick = { onConfigChange(config.copy(imageCustomNamingFormat = \"yyyy-MM-dd_HHmmss_{filename}{ext}\")) }) {' + crlf +
    '                                Icon(Icons.Default.Refresh, "\u91cd\u7f6e\u4e3a\u9ed8\u8ba4")' + crlf +
    '                            }' + crlf +
    '                        }' + crlf +
    '                    )' + crlf +
    '                    Spacer(Modifier.height(4.dp))' + crlf +
    '                    Text(' + crlf +
    '                        "\u53ef\u7528\u5360\u4f4d\u7b26\uff08\u70b9\u51fb\u53ef\u590d\u5236\uff09",' + crlf +
    '                        style = MaterialTheme.typography.labelSmall,' + crlf +
    '                        color = MaterialTheme.colorScheme.primary' + crlf +
    '                    )' + crlf +
    '                    Spacer(Modifier.height(4.dp))' + crlf +
    '                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)' + crlf +
    '                    val tokens = listOf(' + crlf +
    '                        "{filename}" to "\u539f\u6587\u4ef6\u540d\uff08\u4e0d\u542b\u6269\u5c55\u540d\uff09",' + crlf +
    '                        "{ext}" to "\u6269\u5c55\u540d\uff08\u5982 .jpg\u3001.mp3\uff09",' + crlf +
    '                        "yyyy" to "\u5e74\u4efd\uff084\u4f4d\uff09",' + crlf +
    '                        "MM" to "\u6708\u4efd\uff082\u4f4d\uff09",' + crlf +
    '                        "dd" to "\u65e5\uff082\u4f4d\uff09",' + crlf +
    '                        "HH" to "\u5c0f\u65f6\uff0824\u5c0f\u65f6\u5236\uff09",' + crlf +
    '                        "mm" to "\u5206\u949f",' + crlf +
    '                        "ss" to "\u79d2\u949f"' + crlf +
    '                    )' + crlf +
    '                    Column {' + crlf +
    '                        tokens.forEach { (token, desc) ->' + crlf +
    '                            Text(' + crlf +
    '                                text = "$token - $desc",' + crlf +
    '                                style = MaterialTheme.typography.bodySmall,' + crlf +
    '                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),' + crlf +
    '                                modifier = Modifier' + crlf +
    '                                    .fillMaxWidth()' + crlf +
    '                                    .clickable {' + crlf +
    '                                        try {' + crlf +
    '                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(token, token))' + crlf +
    '                                            android.widget.Toast.makeText(context, "\u5df2\u590d\u5236 $token", android.widget.Toast.LENGTH_SHORT).show()' + crlf +
    '                                        } catch (_: Exception) { }' + crlf +
    '                                    }' + crlf +
    '                            )' + crlf +
    '                        }' + crlf +
    '                    }' + crlf +
    '                }'
)

if old in text:
    before = text.index(old)
    after = before + len(old)
    text = text[:before] + new + text[after:]
    changes += 1
    print('2. Custom naming format updated')
else:
    # Try LF only
    old_lf = old.replace(crlf, '\n')
    if old_lf in text:
        text = text.replace(old_lf, new.replace(crlf, '\n'))
        changes += 1
        print('2. Custom naming format updated (LF variant)')
    else:
        print('2. NOT MATCHED - searching...')
        idx = text.find('imageCustomNamingFormat')
        if idx >= 0:
            start = max(0, idx - 100)
            end = min(len(text), idx + 300)
            print('Context around matches:', repr(text[start:end][:300]))

with open(fp, 'w', encoding='utf-8') as f:
    f.write(text)
print(f'Total changes: {changes}')
