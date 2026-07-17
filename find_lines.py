import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read()
lines = content.split(b'\n')

for i, line in enumerate(lines):
    stripped = line.strip()
    if stripped.startswith(b'//') or stripped.startswith(b'ToolbarIconButton'):
        decoded = line.decode('utf-8').rstrip()
        if 'Undo' in decoded or 'Redo' in decoded or 'FormatBold' in decoded:
            print(f'Line {i+1}: {decoded[:80]}')
