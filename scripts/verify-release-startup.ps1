$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$mapping = Join-Path $root 'app/build/outputs/mapping/release/mapping.txt'
if (!(Test-Path -LiteralPath $mapping)) { throw 'Build :app:assembleRelease first.' }
# Compose 1.6 supplies the platform LocalLifecycleOwner directly. Lifecycle 2.8.2
# bridges it via reflection, but its consumer keep rule wrongly expects an array.
if (Select-String -LiteralPath $mapping -Pattern '^androidx.lifecycle.compose.LocalLifecycleOwnerKt' -Quiet) {
    throw 'Unexpected reflective LocalLifecycleOwner bridge in release. Use the Compose platform local.'
}
$shell = Join-Path $root 'feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt'
if (!(Select-String -LiteralPath $shell -Pattern '^import androidx.compose.ui.platform.LocalLifecycleOwner$' -Quiet)) {
    throw 'The startup screen must use the Compose 1.6 platform lifecycle owner.'
}
Write-Output 'Release startup compatibility guard passed. This is not a device launch test.'
