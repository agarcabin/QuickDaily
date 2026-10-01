param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot "../..")
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$htmlPath = (Resolve-Path -LiteralPath (Join-Path $root "tools/widget-previews/widget-previews.html")).Path
$outputDir = Join-Path $root "app/src/main/res/drawable-nodpi"

$edgeCandidates = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
)
$edge = $edgeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $edge) {
    throw "Microsoft Edge was not found. Install a Chromium browser or render the HTML source with an equivalent headless browser."
}

$htmlUri = ([Uri]$htmlPath).AbsoluteUri
$runDir = Join-Path ([System.IO.Path]::GetTempPath()) ("quickdaily-widget-preview-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $runDir | Out-Null

try {
    $renders = @(
        @{ Kind = "read"; Output = "widget_preview_design.png" },
        @{ Kind = "task"; Output = "task_preview_design.png" }
    )
    foreach ($render in $renders) {
        $outputPath = Join-Path $outputDir $render.Output
        $profilePath = Join-Path $runDir $render.Kind
        New-Item -ItemType Directory -Path $profilePath | Out-Null
        $arguments = @(
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--no-first-run",
            "--no-default-browser-check",
            "--run-all-compositor-stages-before-draw",
            "--virtual-time-budget=1000",
            "--window-size=1200,900",
            "--user-data-dir=$profilePath",
            "--screenshot=$outputPath",
            "${htmlUri}?kind=$($render.Kind)"
        )
        & $edge @arguments | Out-Null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $outputPath)) {
            throw "Edge failed to render $($render.Kind) preview."
        }
    }
}
finally {
    if (Test-Path -LiteralPath $runDir) {
        Remove-Item -LiteralPath $runDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Get-ChildItem -LiteralPath $outputDir -Filter "*_preview_design.png" |
    Where-Object { $_.Name -in @("widget_preview_design.png", "task_preview_design.png") } |
    Select-Object Name, Length
