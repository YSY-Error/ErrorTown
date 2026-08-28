[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$WorldPath,
    [string]$OutputFile = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$world = (Resolve-Path $WorldPath).Path
if ([string]::IsNullOrWhiteSpace($OutputFile)) { $OutputFile = Join-Path $PSScriptRoot '..\verification\world-inspection.txt' }
if (-not (Test-Path -LiteralPath $world -PathType Container)) { throw "World directory not found: $world" }
$items = @('level.dat', 'level.dat_old', 'uid.dat', 'paper-world.yml', 'session.lock')
$lines = [Collections.Generic.List[string]]::new()
$lines.Add("World: $world")
$lines.Add("Inspected UTC: $([DateTime]::UtcNow.ToString('o'))")
$lines.Add('Read-only metadata inspection; no world file is modified.')
foreach ($name in $items) {
    $path = Join-Path $world $name
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $file = Get-Item -LiteralPath $path
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        $lines.Add("$name`tpresent`t$($file.Length) bytes`tSHA256 $hash")
    } else {
        $lines.Add("$name`tabsent")
    }
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent ([IO.Path]::GetFullPath($OutputFile))) | Out-Null
Set-Content -LiteralPath $OutputFile -Encoding UTF8 -Value $lines
$lines
