# -*- coding: utf-8 -*-
path = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Find the button area
idx = content.find("BetaLogger.shareLog")
if idx >= 0:
    pre = content.rfind("\n", 0, idx)
    post = content.find("\n", idx)
    snippet = content[pre:post+1]
    print(f"Lines around shareLog:")
    print(repr(snippet))
    
    # Check if the Text() contains garbled chars
    tidx = snippet.find('Text("')
    if tidx >= 0:
        end = snippet.find('")', tidx)
        text_content = snippet[tidx+6:end]
        print(f"Text content: {repr(text_content)}")
        print(f"Text content hex: {text_content.encode('utf-8').hex()}")
else:
    print("BetaLogger.shareLog not found!")
    # Search for any "分享" text
    sx = content.find("分享")
    if sx >= 0:
        print(f"\nFound '分享' at {sx}")
        print(repr(content[sx-20:sx+20]))
    else:
        print("No '分享' found in file")
