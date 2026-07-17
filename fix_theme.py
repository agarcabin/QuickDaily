import re

fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

# 1. Fix the ugly surfaceVariant info card
c = c.replace(
    'colors = CardDefaults.cardColors(\n            containerColor = MaterialTheme.colorScheme.surfaceVariant\n        )',
    'colors = CardDefaults.cardColors(\n            containerColor = MaterialTheme.colorScheme.secondaryContainer\n        )'
)

# 2. Upgrade section cards to ElevatedCard
replacements = [
    ("Card(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {",
     "ElevatedCard(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {"),
    ("Card(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(horizontal = 0.dp)) {",
     "ElevatedCard(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(horizontal = 0.dp)) {"),
    ("Card(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {",
     "ElevatedCard(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {"),
]

for old, new in replacements:
    c = c.replace(old, new)

# 3. The update button card - different padding
c = c.replace(
    "Card(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {",
    "ElevatedCard(modifier = Modifier.fillMaxWidth()) {\n            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {"
)

with open(fp, "w", encoding="utf-8") as f:
    f.write(c)

print("Theme fixes applied")
