[CmdletBinding()]
param(
    [string]$LogPath = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($LogPath)) { $LogPath = Join-Path $PSScriptRoot '..\test-server\logs\latest.log' }
$path = (Resolve-Path $LogPath).Path
$text = Get-Content -Raw -LiteralPath $path
$patterns = @('ErrorTown.*(Exception|Error)', 'Disallowed chat character', 'No key layers')
foreach ($pattern in $patterns) {
    $matches = Select-String -InputObject $text -Pattern $pattern -AllMatches
    if ($matches) { Write-Output "MATCH $pattern"; $matches | ForEach-Object Line }
}
if ($text -match 'Done \([0-9.]+s\)!') { Write-Output 'PASS: Paper reached Done marker' } else { Write-Output 'WARN: Paper Done marker not found' }
