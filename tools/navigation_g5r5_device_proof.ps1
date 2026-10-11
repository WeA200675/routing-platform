param(
    [string] $Serial =
        "62030DLCH0014M"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repo =
    "C:\GitHub\Routenplanung\routing-platform-foundation-0.2"

$package =
    "org.routingplatform.app"

$probeComponent =
    "org.routingplatform.app/.navigation.G5R5FaultProbeActivity"

$probeResourceId =
    "org.routingplatform.app:id/g5r5_probe_status"

$apk =
    Join-Path `
        $repo `
        "platform\android\app\build\outputs\apk\debug\app-debug.apk"

$harness =
    Join-Path `
        $repo `
        "tools\navigation_device_harness.ps1"

. $harness

function Invoke-G5R5Probe {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Mode
    )

    Write-Host
    Write-Host (
        "===== G5R5 PROBE: {0} =====" -f
            $Mode
    )

    try {
        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "am",
                "force-stop",
                $package
            ) |
            Out-Null

        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "input",
                "keyevent",
                "KEYCODE_WAKEUP"
            ) |
            Out-Null

        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "wm",
                "dismiss-keyguard"
            ) `
            -AllowFailure |
            Out-Null

        Normalize-RoutingPlatformSystemUi `
            -Adb $Adb `
            -Serial $Serial

        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "am",
                "start",
                "-W",
                "-n",
                $probeComponent,
                "--es",
                "mode",
                $Mode
            ) |
            Out-Null

        $result =
            Wait-RoutingPlatformUiNode `
                -Adb $Adb `
                -Serial $Serial `
                -ResourceId $probeResourceId `
                -TimeoutSeconds 15

        $text =
            $result.Node.GetAttribute(
                "text"
            )

        $expectedPrefix =
            "PASS|" +
                $Mode +
                "|"

        if (
            -not $text.StartsWith(
                $expectedPrefix
            )
        ) {
            throw (
                "Probe did not pass.`nMode: {0}`nStatus: {1}" -f
                    $Mode,
                    $text
            )
        }

        Assert-RoutingPlatformProcessAlive `
            -Adb $Adb `
            -Serial $Serial `
            -Package $package

        Write-Host (
            "PASS {0}: {1}" -f
                $Mode,
                $text
        )
    }
    catch {
        $evidence =
            Save-RoutingPlatformEvidence `
                -Adb $Adb `
                -Serial $Serial `
                -Package $package `
                -Repo $repo `
                -CaseName (
                    "g5r5-" +
                        $Mode
                )

        Write-Host "Evidence: $evidence"

        throw
    }
}

function Get-G5R5PermissionState {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb
    )

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "am",
            "force-stop",
            $package
        ) |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "input",
            "keyevent",
            "KEYCODE_WAKEUP"
        ) |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "wm",
            "dismiss-keyguard"
        ) `
        -AllowFailure |
        Out-Null

    Normalize-RoutingPlatformSystemUi `
        -Adb $Adb `
        -Serial $Serial

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            $probeComponent,
            "--es",
            "mode",
            "permission_state"
        ) |
        Out-Null

    $result =
        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId $probeResourceId `
            -TimeoutSeconds 15

    $text =
        $result.Node.GetAttribute(
            "text"
        )

    $prefix =
        "PASS|permission_state|"

    if (
        -not $text.StartsWith(
            $prefix
        )
    ) {
        throw (
            "Permission-state probe failed: {0}" -f
                $text
        )
    }

    return $text.Substring(
        $prefix.Length
    )
}

function Get-RoutingPlatformCurrentAndroidUser {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb
    )

    $output =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "shell",
                    "am",
                    "get-current-user"
                )
        )

    $currentUser =
        (
            $output -join
                ""
        ).Trim()

    if (
        $currentUser -notmatch
            '^[0-9]+$'
    ) {
        throw (
            "Could not resolve current Android user: {0}" -f
                $currentUser
        )
    }

    return $currentUser
}

Write-Host "===================================================="
Write-Host " G5R5B PIXEL BOUNDARY PROOF"
Write-Host " DEBUG-ONLY PROBE / NO NAVIGATION-TRUTH MUTATION"
Write-Host "===================================================="

$adb =
    Resolve-RoutingPlatformAdb

Assert-RoutingPlatformDevice `
    -Adb $adb `
    -Serial $Serial

$apkHash =
    Install-RoutingPlatformApkVerified `
        -Adb $adb `
        -Serial $Serial `
        -Package $package `
        -ApkPath $apk

Write-Host "APK identity : $apkHash"

foreach ($mode in @(
    "geocoder_unavailable"
    "geocoder_lookup_failure"
    "persistence_corrupt_read"
    "persistence_write_retry"
    "map_failure"
    "planning_stale"
    "stale_callback"
    "jni_illegal"
)) {
    Invoke-G5R5Probe `
        -Adb $adb `
        -Mode $mode
}

Write-Host
Write-Host "===== G5R5 PROBE: permission_missing ====="

$currentAndroidUser =
    Get-RoutingPlatformCurrentAndroidUser `
        -Adb $adb

$permissionBefore =
    Get-G5R5PermissionState `
        -Adb $adb

$fineBefore =
    $permissionBefore.Contains(
        "fine=granted"
    )

$coarseBefore =
    $permissionBefore.Contains(
        "coarse=granted"
    )

Write-Host (
    "Android user      : {0}" -f
        $currentAndroidUser
)

Write-Host (
    "Permission before : {0}" -f
        $permissionBefore
)

try {
    if (
        $fineBefore
    ) {
        Invoke-RoutingPlatformAdb `
            -Adb $adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "pm",
                "revoke",
                "--user",
                $currentAndroidUser,
                $package,
                "android.permission.ACCESS_FINE_LOCATION"
            ) |
            Out-Null
    }

    $permissionDuring =
        Get-G5R5PermissionState `
            -Adb $adb

    Write-Host (
        "Permission during : {0}" -f
            $permissionDuring
    )

    if (
        $permissionDuring.Contains(
            "fine=granted"
        )
    ) {
        throw (
            "ACCESS_FINE_LOCATION is still granted for Android user {0}." -f
                $currentAndroidUser
        )
    }

    Invoke-G5R5Probe `
        -Adb $adb `
        -Mode "permission_missing"
}
finally {
    if (
        $coarseBefore
    ) {
        Invoke-RoutingPlatformAdb `
            -Adb $adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "pm",
                "grant",
                "--user",
                $currentAndroidUser,
                $package,
                "android.permission.ACCESS_COARSE_LOCATION"
            ) |
            Out-Null
    }

    if (
        $fineBefore
    ) {
        Invoke-RoutingPlatformAdb `
            -Adb $adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "pm",
                "grant",
                "--user",
                $currentAndroidUser,
                $package,
                "android.permission.ACCESS_FINE_LOCATION"
            ) |
            Out-Null
    }
}

$permissionAfter =
    Get-G5R5PermissionState `
        -Adb $adb

Write-Host (
    "Permission after  : {0}" -f
        $permissionAfter
)

if (
    $permissionAfter -ne
        $permissionBefore
) {
    throw (
        "Location permission state was not restored exactly.`nBefore: {0}`nAfter : {1}" -f
            $permissionBefore,
            $permissionAfter
    )
}

Write-Host "Permission restore : EXACT/PASS"

Write-Host
Write-Host "===================================================="
Write-Host " G5R5B PIXEL BOUNDARY PROOF: PASS"
Write-Host "===================================================="
Write-Host "Geocoder fault model      : TYPED/PASS"
Write-Host "Persistence corrupt read  : STORE FALLBACK/PASS"
Write-Host "Persistence write retry   : EXACTLY ONE/PASS"
Write-Host "Map recovery policy       : ONE RETRY THEN FAIL-CLOSED"
Write-Host "Stale planning location   : REJECTED"
Write-Host "Missing FINE permission   : REJECTED/RESTORED"
Write-Host "Stale async callback      : IGNORED"
Write-Host "Illegal JNI transition    : REJECTED"
Write-Host "Navigation truth          : NOT FABRICATED/ADVANCED"
Write-Host "===================================================="
