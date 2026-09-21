param([Parameter(Mandatory=$true)][string]$PlantUmlJar, [string]$Java = 'java')
$ErrorActionPreference = 'Stop'
$jar = (Resolve-Path -LiteralPath $PlantUmlJar).Path
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.puml' | Sort-Object Name)
foreach ($format in @('png', 'svg')) {
    & $Java '-Djava.awt.headless=true' '-jar' $jar '-charset' 'UTF-8' '-failfast2' "-t$format" @($sources.FullName)
    if ($LASTEXITCODE -ne 0) { throw "PlantUML $format render failed: $LASTEXITCODE" }
}
foreach ($source in $sources) {
    $png = [IO.Path]::ChangeExtension($source.FullName, 'png')
    $svg = [IO.Path]::ChangeExtension($source.FullName, 'svg')
    $bytes = [IO.File]::ReadAllBytes($png)
    if ($bytes.Length -lt 24 -or [BitConverter]::ToString($bytes, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') { throw "Invalid PNG: $png" }
    [xml]$xml = Get-Content -LiteralPath $svg -Raw
    if ($xml.DocumentElement.LocalName -ne 'svg') { throw "Invalid SVG: $svg" }
    Write-Output "Verified: $($source.BaseName) (PNG + SVG)"
}
