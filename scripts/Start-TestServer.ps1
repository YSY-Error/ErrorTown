[CmdletBinding()]
param(
    [string]$ServerPath = '',
    [int]$MemoryMb = 2048
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($ServerPath)) { $ServerPath = Join-Path $PSScriptRoot '..\test-server' }
$server = (Resolve-Path $ServerPath).Path
$paper = Join-Path $server 'paper-1.21.8-60.jar'
if (-not (Test-Path -LiteralPath $paper -PathType Leaf)) { throw "Paper JAR not found. Run New-TestServer.ps1 first." }
Push-Location $server
try { & java "-Xms${MemoryMb}M" "-Xmx${MemoryMb}M" -jar $paper --nogui } finally { Pop-Location }
