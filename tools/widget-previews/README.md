# QuickDaily widget previews

`widget-previews.html` is the reproducible HTML/CSS source for the 4:3 picker
previews. Its card styling and content are based on the supplied
`quickdaily-widget-previews-rendered.html` selection-page visual, while the
read/note and task cards themselves use a 4:3 aspect ratio. It renders both
previews at 1200 × 900 px.

From the repository root, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\widget-previews\render-preview.ps1
```

The script uses the locally installed Microsoft Edge headless renderer and
updates only `widget_preview_design.png` and `task_preview_design.png`. The
QuickDaily quick-entry preview remains unchanged.
