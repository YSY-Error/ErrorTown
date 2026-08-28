[CmdletBinding()]
param(
    [string]$Destination = '',
    [string]$MaintainedJar = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Destination)) { $Destination = Join-Path $PSScriptRoot '..\test-server' }
if ([string]::IsNullOrWhiteSpace($MaintainedJar)) { $MaintainedJar = Join-Path $PSScriptRoot '..\build\libs\ErrorTown-2.1.6.0.jar' }
$destinationPath = [IO.Path]::GetFullPath($Destination)
$liveRoot = (Resolve-Path $root).Path
if ($destinationPath.StartsWith($liveRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -eq $false) {
    throw "Test server must be below the workspace root: $destinationPath"
}
if ($destinationPath -eq $liveRoot) { throw 'Refusing to use the live server root as test server.' }
if (Test-Path -LiteralPath $destinationPath) {
    if (-not $Force) { throw "Destination exists. Use -Force only for this isolated test directory: $destinationPath" }
    Remove-Item -LiteralPath $destinationPath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $destinationPath, (Join-Path $destinationPath 'plugins') | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'paper-1.21.8-60.jar') -Destination $destinationPath
Copy-Item -LiteralPath (Join-Path $root 'eula.txt') -Destination $destinationPath
Copy-Item -LiteralPath (Join-Path $root 'server.properties') -Destination $destinationPath
Copy-Item -LiteralPath $MaintainedJar -Destination (Join-Path $destinationPath 'plugins\ErrorTown-2.1.6.0.jar')
$propertiesPath = Join-Path $destinationPath 'server.properties'
$properties = Get-Content -LiteralPath $propertiesPath
$overrides = [ordered]@{
    'server-ip' = '127.0.0.1'
    'server-port' = '25576'
    'query.port' = '25576'
    'level-name' = 'test-world'
    'max-players' = '2'
    'enable-rcon' = 'false'
}
foreach ($key in $overrides.Keys) {
    $pattern = '^' + [regex]::Escape($key) + '='
    if ($properties -match $pattern) {
        $properties = $properties | ForEach-Object { if ($_ -match $pattern) { "$key=$($overrides[$key])" } else { $_ } }
    } else {
        $properties += "$key=$($overrides[$key])"
    }
}
Set-Content -LiteralPath $propertiesPath -Encoding ASCII -Value $properties

$dependencyPatterns = @('Vault*.jar', 'EssentialsX-*.jar', 'PlaceholderAPI*.jar', 'ProtocolLib*.jar', '*nbt-api-plugin*.jar')
foreach ($pattern in $dependencyPatterns) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $root 'plugins') -Filter $pattern -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { Copy-Item -LiteralPath $candidate.FullName -Destination (Join-Path $destinationPath 'plugins') }
}
Set-Content -LiteralPath (Join-Path $destinationPath 'README.md') -Encoding UTF8 -Value @'
# ErrorTown isolated test server

This directory is disposable and must never share `ErrorTownWorld`, `world`, or `plugins/ErrorTown` with the live server. Run `..	oolsStart-TestServer.ps1` from this directory. Use a test player and record all commands in `..erificationacceptance-results.md`.
'@
Write-Output "Created isolated server: $destinationPath"
