param([string]$Variant = 'release')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$javap = Join-Path $env:JAVA_HOME 'bin/javap.exe'
if (!(Test-Path -LiteralPath $javap)) { throw 'Set JAVA_HOME to JDK 17 first.' }
$modules = @('app', 'core/model', 'core/common', 'core/design', 'core/data', 'feature/common', 'feature/browse', 'feature/detail', 'feature/player', 'feature/state', 'feature/shell')
$dirs = $modules | ForEach-Object { Join-Path $root "$_/build/tmp/kotlin-classes/$Variant" }
$classPath = $dirs -join [IO.Path]::PathSeparator
$cache = @{}
$count = 0
foreach ($rule in Get-Content (Join-Path $root 'app/src/main/baseline-prof.txt')) {
    if ([string]::IsNullOrWhiteSpace($rule) -or $rule.StartsWith('#')) { continue }
    if ($rule -notmatch '^[HSP]*L([^;]+);->([^\(]+)(\(.*)$') { throw "Unsupported exact rule: $rule" }
    $class = $Matches[1].Replace('/', '.')
    $method = $Matches[2]
    $descriptor = $Matches[3]
    if (!$cache.ContainsKey($class)) {
        $lines = & $javap -p -s -classpath $classPath $class 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Missing profile class: $class" }
        $cache[$class] = $lines
    }
    $lines = $cache[$class]
    $name = if ($method -eq '<init>') { $class } else { $method }
    $found = $false
    for ($i = 0; $i -lt $lines.Count - 1; $i++) {
        if ($lines[$i] -match ('\b' + [regex]::Escape($name) + '\(') -and
            $lines[$i + 1].Trim() -eq "descriptor: $descriptor") { $found = $true; break }
    }
    if (!$found) { throw "Stale profile method: $rule" }
    $count++
}
Write-Output "Verified $count exact Baseline Profile method rules against $Variant bytecode."
