param(
    [string] $Serial =
        "62030DLCH0014M",

    [int] $StationarySeconds =
        20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repo =
    "C:\GitHub\Routenplanung\routing-platform-foundation-0.2"

$package =
    "org.routingplatform.app"

$mainComponent =
    "org.routingplatform.app/.MainActivity"

$controlComponent =
    "org.routingplatform.app/.navigation.G5R6DriveProofControlActivity"

$permissionProbeComponent =
    "org.routingplatform.app/.navigation.G5R5FaultProbeActivity"

$primaryActionId =
    "rp.navigation.primary_action"

$previewStateId =
    "rp.navigation.session.Preview"

$navigatingStateId =
    "rp.navigation.session.Navigating"

$controlStatusId =
    "org.routingplatform.app:id/g5r6_drive_proof_status"

$permissionStatusId =
    "org.routingplatform.app:id/g5r5_probe_status"

$apk =
    Join-Path `
        $repo `
        "platform\android\app\build\outputs\apk\debug\app-debug.apk"

$harness =
    Join-Path `
        $repo `
        "tools\navigation_device_harness.ps1"

$exportTool =
    Join-Path `
        $repo `
        "tools\navigation_drive_proof_export.ps1"

. $harness

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
        throw "Could not resolve current Android user: $currentUser"
    }

    return $currentUser
}

function Get-G5R6PermissionState {
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
            "start",
            "-W",
            "-n",
            $permissionProbeComponent,
            "--es",
            "mode",
            "permission_state"
        ) |
        Out-Null

    $result =
        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId $permissionStatusId `
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
        throw "Permission-state probe failed: $text"
    }

    return $text.Substring(
        $prefix.Length
    )
}

function Set-G5R6Permission {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $AndroidUser,

        [Parameter(Mandatory = $true)]
        [string] $Permission,

        [Parameter(Mandatory = $true)]
        [bool] $Granted
    )

    $verb =
        "revoke"

    if ($Granted) {
        $verb =
            "grant"
    }

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "pm",
            $verb,
            "--user",
            $AndroidUser,
            $package,
            $Permission
        ) |
        Out-Null
}

function Invoke-G5R6Control {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [ValidateSet(
            "arm",
            "stop",
            "status"
        )]
        [string] $Mode,

        [string] $CaptureId =
            ""
    )

    $arguments =
        @(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            $controlComponent,
            "--es",
            "mode",
            $Mode
        )

    if (
        -not [string]::IsNullOrWhiteSpace(
            $CaptureId
        )
    ) {
        $arguments +=
            @(
                "--es",
                "captureId",
                $CaptureId
            )
    }

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments $arguments |
        Out-Null

    $result =
        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId $controlStatusId `
            -TimeoutSeconds 15

    $text =
        $result.Node.GetAttribute(
            "text"
        )

    $prefix =
        "PASS|" +
            $Mode +
            "|"

    if (
        -not $text.StartsWith(
            $prefix
        )
    ) {
        throw "Drive-proof control failed: $text"
    }

    Write-Host "CONTROL $Mode : $text"

    return $text.Substring(
        $prefix.Length
    )
}

function Show-G5R6MainActivity {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb
    )

    # The debug control Activity is launched from adb and can remain on top
    # of the existing MainActivity task. CLEAR_TOP + SINGLE_TOP explicitly
    # returns to the existing MainActivity without fabricating navigation
    # state or restarting the app process.
    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "am",
            "start",
            "-W",
            "-f",
            "0x24000000",
            "-n",
            $mainComponent
        ) |
        Out-Null

    Normalize-RoutingPlatformSystemUi `
        -Adb $Adb `
        -Serial $Serial

    $deadline =
        (Get-Date).AddSeconds(
            10
        )

    $lastResumed =
        "<none>"

    while (
        (Get-Date) -lt
            $deadline
    ) {
        $activityLines =
            @(
                Invoke-RoutingPlatformAdb `
                    -Adb $Adb `
                    -Serial $Serial `
                    -CommandArguments @(
                        "shell",
                        "dumpsys",
                        "activity",
                        "activities"
                    )
            )

        $resumedLines =
            @(
                $activityLines |
                Where-Object {
                    (
                        $_.Contains(
                            "topResumedActivity"
                        ) -or
                        $_.Contains(
                            "ResumedActivity:"
                        )
                    )
                }
            )

        if (@($resumedLines).Count -gt 0) {
            $lastResumed =
                $resumedLines -join
                    " | "
        }

        $mainResumed =
            @(
                $resumedLines |
                Where-Object {
                    $_.Contains(
                        $mainComponent
                    )
                }
            )

        if (@($mainResumed).Count -gt 0) {
            Write-Host "Foreground return : MAINACTIVITY/PASS"
            return
        }

        Start-Sleep `
            -Milliseconds 250
    }

    if (
        $lastResumed.Contains(
            "G5R6DriveProofControlActivity"
        )
    ) {
        throw (
            "Debug control Activity remained foreground after explicit CLEAR_TOP/SINGLE_TOP return. Resumed: {0}" -f
                $lastResumed
        )
    }

    throw (
        "MainActivity did not become resumed after debug control. Resumed: {0}" -f
            $lastResumed
    )
}

function Invoke-G5R6UiClick {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $ResourceId,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $result =
        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId $ResourceId `
            -TimeoutSeconds 20

    Assert-RoutingPlatformUiNodeEnabled `
        -Node $result.Node `
        -Label $Label

    $bounds =
        $result.Node.GetAttribute(
            "bounds"
        )

    if (
        $bounds -notmatch
            '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$'
    ) {
        throw "Could not parse UI bounds for ${Label}: $bounds"
    }

    $left =
        [int]$Matches[1]

    $top =
        [int]$Matches[2]

    $right =
        [int]$Matches[3]

    $bottom =
        [int]$Matches[4]

    $x =
        [int](
            (
                $left +
                $right
            ) /
            2
        )

    $y =
        [int](
            (
                $top +
                $bottom
            ) /
            2
        )

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "input",
            "tap",
            $x.ToString(),
            $y.ToString()
        ) |
        Out-Null
}

function Get-AnchorShapePosition {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Anchor
    )

    return (
        [double]$Anchor.shapeSegmentIndex +
        [double]$Anchor.segmentFraction
    )
}

if ($StationarySeconds -lt 10) {
    throw "StationarySeconds must be at least 10."
}

Write-Host "===================================================="
Write-Host " G5R6C PIXEL STATIONARY SANITY + DRIVE-PROOF PREP"
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

$currentAndroidUser =
    Get-RoutingPlatformCurrentAndroidUser `
        -Adb $adb

$permissionBefore =
    Get-G5R6PermissionState `
        -Adb $adb

$fineBefore =
    $permissionBefore.Contains(
        "fine=granted"
    )

$coarseBefore =
    $permissionBefore.Contains(
        "coarse=granted"
    )

$timestamp =
    Get-Date `
        -Format "yyyyMMdd-HHmmssfff"

$captureId =
    "g5r6-stationary-" +
        $timestamp

$evidenceDirectory =
    Join-Path `
        $env:TEMP `
        (
            "routing-platform-g5r6c-" +
            $timestamp
        )

[System.IO.Directory]::CreateDirectory(
    $evidenceDirectory
) |
    Out-Null

$navigationStarted =
    $false

try {
    Set-G5R6Permission `
        -Adb $adb `
        -AndroidUser $currentAndroidUser `
        -Permission "android.permission.ACCESS_COARSE_LOCATION" `
        -Granted $true

    Set-G5R6Permission `
        -Adb $adb `
        -AndroidUser $currentAndroidUser `
        -Permission "android.permission.ACCESS_FINE_LOCATION" `
        -Granted $true

    $permissionDuring =
        Get-G5R6PermissionState `
            -Adb $adb

    if (
        -not $permissionDuring.Contains(
            "fine=granted"
        ) -or
        -not $permissionDuring.Contains(
            "coarse=granted"
        )
    ) {
        throw "Precise location permission was not established for G5R6C."
    }

    Start-RoutingPlatformApp `
        -Adb $adb `
        -Serial $Serial `
        -Package $package `
        -Component $mainComponent

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $previewStateId `
        -TimeoutSeconds 20 |
        Out-Null

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $primaryActionId `
        -TimeoutSeconds 20 |
        Out-Null

    Invoke-G5R6Control `
        -Adb $adb `
        -Mode "arm" `
        -CaptureId $captureId |
        Out-Null

    Show-G5R6MainActivity `
        -Adb $adb

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $previewStateId `
        -TimeoutSeconds 20 |
        Out-Null

    Invoke-G5R6UiClick `
        -Adb $adb `
        -ResourceId $primaryActionId `
        -Label "navigation primary action"

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $navigatingStateId `
        -TimeoutSeconds 20 |
        Out-Null

    $navigationStarted =
        $true

    Write-Host (
        "Stationary observation window : {0}s" -f
            $StationarySeconds
    )

    Start-Sleep `
        -Seconds $StationarySeconds

    Show-G5R6MainActivity `
        -Adb $adb

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $navigatingStateId `
        -TimeoutSeconds 20 |
        Out-Null

    Invoke-G5R6UiClick `
        -Adb $adb `
        -ResourceId $primaryActionId `
        -Label "navigation stop action"

    Wait-RoutingPlatformUiNode `
        -Adb $adb `
        -Serial $Serial `
        -ResourceId $previewStateId `
        -TimeoutSeconds 20 |
        Out-Null

    $navigationStarted =
        $false

    $stopDetail =
        Invoke-G5R6Control `
            -Adb $adb `
            -Mode "stop"

    if (
        $stopDetail -notmatch
            '^capture=([^;]+);events=(\d+);bytes=(\d+)$'
    ) {
        throw "Could not parse drive-proof stop summary: $stopDetail"
    }

    if ($Matches[1] -ne $captureId) {
        throw "Stopped capture id does not match armed capture."
    }

    $recordedEvents =
        [int]$Matches[2]

    if ($recordedEvents -lt 2) {
        throw "Drive-proof capture contains too few runtime observations."
    }

    & powershell.exe `
        -NoLogo `
        -NoProfile `
        -NonInteractive `
        -ExecutionPolicy Bypass `
        -File $exportTool `
        -CaptureId $captureId `
        -Serial $Serial `
        -OutputDirectory $evidenceDirectory

    $exportExit =
        $LASTEXITCODE

    if ($exportExit -ne 0) {
        throw "Drive-proof export failed with exit code $exportExit."
    }

    $jsonl =
        Join-Path `
            $evidenceDirectory `
            (
                "routing-platform-drive-proof-" +
                $captureId +
                ".jsonl"
            )

    if (-not (Test-Path -LiteralPath $jsonl)) {
        throw "Drive-proof JSONL export is missing."
    }

    $events =
        @(
            Get-Content `
                -LiteralPath $jsonl `
                -Encoding UTF8 |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace(
                    $_
                )
            } |
            ForEach-Object {
                $_ |
                    ConvertFrom-Json
            }
        )

    if (@($events).Count -ne $recordedEvents) {
        throw "Exported event count differs from recorder summary."
    }

    $observedPositionCount =
        0

    $nativeAcceptedCount =
        0

    $unbackedProgressCount =
        0

    $nativeFailureCount =
        0

    $previousAcceptedShape =
        $null

    foreach ($event in $events) {
        if ($null -ne $event.observedPosition) {
            $observedPositionCount +=
                1
        }

        if (
            [string]$event.pipelineStatus -eq
                "NativeUpdateFailed"
        ) {
            $nativeFailureCount +=
                1
        }

        $accepted =
            [bool]$event.nativeUpdateAccepted

        if ($accepted) {
            $nativeAcceptedCount +=
                1

            if ($null -eq $event.observedPosition) {
                throw "Native acceptance exists without an observed position."
            }
        }

        if ($null -ne $event.acceptedProgress) {
            $shape =
                Get-AnchorShapePosition `
                    -Anchor $event.acceptedProgress

            if (
                $null -ne $previousAcceptedShape -and
                $shape -gt
                    $previousAcceptedShape -and
                -not $accepted
            ) {
                $unbackedProgressCount +=
                    1
            }

            $previousAcceptedShape =
                $shape
        }
    }

    if ($observedPositionCount -lt 1) {
        throw "Stationary capture contains no real runtime position observation."
    }

    if ($nativeFailureCount -ne 0) {
        throw "Stationary capture contains a native update failure."
    }

    if ($unbackedProgressCount -ne 0) {
        throw "Accepted progress advanced without a matching native acceptance."
    }

    $jsonlSha =
        (
            Get-FileHash `
                -LiteralPath $jsonl `
                -Algorithm SHA256
        ).Hash

    Write-Host
    Write-Host "===================================================="
    Write-Host " G5R6C PIXEL STATIONARY SANITY: PASS"
    Write-Host "===================================================="
    Write-Host "Capture                : $captureId"
    Write-Host "Events                 : $recordedEvents"
    Write-Host "Observed positions     : $observedPositionCount"
    Write-Host "Native accepts         : $nativeAcceptedCount"
    Write-Host "Native failures        : $nativeFailureCount"
    Write-Host "Unbacked progress      : $unbackedProgressCount"
    Write-Host "JSONL SHA256           : $jsonlSha"
    Write-Host "Evidence               : $evidenceDirectory"
    Write-Host "Hardcoded taps         : NONE"
    Write-Host "Synthetic location     : NONE"
    Write-Host "Synthetic progress     : NONE"
    Write-Host "Physical drive proof   : STILL PENDING"
    Write-Host "===================================================="
}
catch {
    $evidence =
        Save-RoutingPlatformEvidence `
            -Adb $adb `
            -Serial $Serial `
            -Package $package `
            -Repo $repo `
            -CaseName "g5r6c-stationary"

    Write-Host "Failure evidence: $evidence"

    throw
}
finally {
    if ($navigationStarted) {
        try {
            Show-G5R6MainActivity `
                -Adb $adb

            Invoke-G5R6UiClick `
                -Adb $adb `
                -ResourceId $primaryActionId `
                -Label "navigation cleanup stop"
        } catch {
            Write-Host (
                "Navigation cleanup warning: {0}" -f
                    $_.Exception.Message
            )
        }
    }

    try {
        Invoke-G5R6Control `
            -Adb $adb `
            -Mode "status" |
            Out-Null
    } catch {
        # Best-effort only; process/activity may already be stopped.
    }

    Set-G5R6Permission `
        -Adb $adb `
        -AndroidUser $currentAndroidUser `
        -Permission "android.permission.ACCESS_FINE_LOCATION" `
        -Granted $fineBefore

    Set-G5R6Permission `
        -Adb $adb `
        -AndroidUser $currentAndroidUser `
        -Permission "android.permission.ACCESS_COARSE_LOCATION" `
        -Granted $coarseBefore

    $permissionAfter =
        Get-G5R6PermissionState `
            -Adb $adb

    if ($permissionAfter -ne $permissionBefore) {
        throw (
            "Location permission state was not restored exactly.`nBefore: {0}`nAfter : {1}" -f
                $permissionBefore,
                $permissionAfter
        )
    }

    Write-Host "Permission restore : EXACT/PASS"
}
