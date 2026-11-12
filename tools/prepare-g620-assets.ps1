param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$assetSource = Join-Path $SourceRoot 'Qwen2.5-1.5B-Instruct-Q4_K_M.gguf'
$runtimeSource = Join-Path $SourceRoot 'libsocial_ai_llama.so'
$assetTarget = Join-Path $projectRoot 'platform/android/app/src/main/assets/g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf'
$runtimeTarget = Join-Path $projectRoot 'platform/android/app/src/main/jniLibs/arm64-v8a/libsocial_ai_llama.so'

foreach ($path in @($assetSource, $runtimeSource)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "G620-Datei fehlt: $path"
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path $assetTarget), (Split-Path $runtimeTarget) | Out-Null
Copy-Item -LiteralPath $assetSource -Destination $assetTarget -Force
Copy-Item -LiteralPath $runtimeSource -Destination $runtimeTarget -Force

$expected = @{
    $assetTarget = '1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370'
    $runtimeTarget = '8fae0f88d57a8ef6751ea73e63d690b6c6471c07f8cc3e82dd73c2dc9a84cbf1'
}
foreach ($entry in $expected.GetEnumerator()) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $entry.Key).Hash.ToLowerInvariant()
    if ($actual -ne $entry.Value) { throw "SHA-256 stimmt nicht: $($entry.Key) ($actual)" }
    Write-Host "OK $($entry.Key) $actual"
}
