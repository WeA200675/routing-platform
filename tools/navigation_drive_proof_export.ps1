param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[A-Za-z0-9._-]{1,80}$")]
    [string] $CaptureId,

    [string] $Serial =
        "62030DLCH0014M",

    [string] $OutputDirectory =
        "$env:USERPROFILE\Downloads"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repo =
    "C:\GitHub\Routenplanung\routing-platform-foundation-0.2"

$package =
    "org.routingplatform.app"

$harness =
    Join-Path `
        $repo `
        "tools\navigation_device_harness.ps1"

. $harness

function Invoke-DriveProofExecOut {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $OutputPath,

        [Parameter(Mandatory = $true)]
        [string] $ErrorPath
    )

    $process =
        Start-Process `
            -FilePath $Adb `
            -ArgumentList $Arguments `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $OutputPath `
            -RedirectStandardError $ErrorPath

    if ($process.ExitCode -ne 0) {
        $stderr =
            if (
                Test-Path -LiteralPath $ErrorPath
            ) {
                [System.IO.File]::ReadAllText(
                    $ErrorPath
                ).Trim()
            } else {
                ""
            }

        throw (
            "adb exec-out failed ({0}): {1}" -f
                $process.ExitCode,
                $stderr
        )
    }
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

Write-Host "===================================================="
Write-Host " G5R6 DRIVE-PROOF EXPORT"
Write-Host " READ-ONLY DEBUG APP EVIDENCE EXPORT"
Write-Host "===================================================="

$adb =
    Resolve-RoutingPlatformAdb

Assert-RoutingPlatformDevice `
    -Adb $adb `
    -Serial $Serial

if (
    -not (
        Test-Path -LiteralPath $OutputDirectory
    )
) {
    [System.IO.Directory]::CreateDirectory(
        $OutputDirectory
    ) |
        Out-Null
}

$remotePath =
    "files/navigation-drive-proof/" +
        $CaptureId +
        ".jsonl"

$outputPath =
    Join-Path `
        $OutputDirectory `
        (
            "routing-platform-drive-proof-" +
            $CaptureId +
            ".jsonl"
        )

$errorPath =
    $outputPath +
        ".stderr.txt"

Invoke-DriveProofExecOut `
    -Adb $adb `
    -Arguments @(
        "-s",
        $Serial,
        "exec-out",
        "run-as",
        $package,
        "cat",
        $remotePath
    ) `
    -OutputPath $outputPath `
    -ErrorPath $errorPath

if (
    -not (
        Test-Path -LiteralPath $outputPath
    ) -or
    (
        Get-Item -LiteralPath $outputPath
    ).Length -le
        0
) {
    throw "Exported drive-proof JSONL is empty."
}

$lines =
    @(
        Get-Content `
            -LiteralPath $outputPath `
            -Encoding UTF8 |
        Where-Object {
            -not [string]::IsNullOrWhiteSpace(
                $_
            )
        }
    )

if (@($lines).Count -eq 0) {
    throw "Export contains no drive-proof observations."
}

$previousSequence =
    0L

$previousAcceptedShape =
    $null

$sessionId =
    $null

$routeId =
    $null

$nativeAcceptedCount =
    0

foreach ($line in $lines) {
    $event =
        $line |
        ConvertFrom-Json

    if (
        [int]$event.schemaVersion -ne
            1
    ) {
        throw "Unexpected drive-proof schema version."
    }

    $sequence =
        [long]$event.sequence

    if (
        $sequence -ne
            $previousSequence +
                1L
    ) {
        throw (
            "Non-monotonic drive-proof sequence: {0} after {1}" -f
                $sequence,
                $previousSequence
        )
    }

    $previousSequence =
        $sequence

    if (
        [string]::IsNullOrWhiteSpace(
            [string]$event.sessionId
        ) -or
        [string]::IsNullOrWhiteSpace(
            [string]$event.routeId
        )
    ) {
        throw "Drive-proof event has blank session/route identity."
    }

    if ($null -eq $sessionId) {
        $sessionId =
            [string]$event.sessionId

        $routeId =
            [string]$event.routeId
    } elseif (
        [string]$event.sessionId -ne
            $sessionId -or
        [string]$event.routeId -ne
            $routeId
    ) {
        throw "Drive-proof identity changed inside one capture."
    }

    $attempted =
        [bool]$event.nativeUpdateAttempted

    $accepted =
        [bool]$event.nativeUpdateAccepted

    if (
        $accepted -and
        -not $attempted
    ) {
        throw "Native acceptance exists without a native attempt."
    }

    if (
        [string]$event.pipelineStatus -eq
            "NativeProgressUpdated" -and
        -not $accepted
    ) {
        throw "NativeProgressUpdated exists without native acceptance."
    }

    if (
        [string]$event.pipelineStatus -eq
            "NativeUpdateFailed"
    ) {
        if (
            -not $attempted -or
            $accepted -or
            [string]::IsNullOrWhiteSpace(
                [string]$event.nativeFailureClass
            )
        ) {
            throw "NativeUpdateFailed evidence is internally inconsistent."
        }
    }

    if ($null -ne $event.acceptedProgress) {
        $shape =
            Get-AnchorShapePosition `
                -Anchor $event.acceptedProgress

        if (
            $null -ne $previousAcceptedShape -and
            $shape -lt
                $previousAcceptedShape
        ) {
            throw "Accepted native progress regressed inside the capture."
        }

        if ($accepted) {
            if (
                $null -ne $previousAcceptedShape -and
                $shape -le
                    $previousAcceptedShape
            ) {
                throw "Accepted native update was not strictly forward."
            }

            $nativeAcceptedCount +=
                1
        }

        $previousAcceptedShape =
            $shape
    } elseif ($accepted) {
        throw "Native update accepted without acceptedProgress evidence."
    }
}

$sha =
    (
        Get-FileHash `
            -LiteralPath $outputPath `
            -Algorithm SHA256
    ).Hash

Write-Host
Write-Host "===================================================="
Write-Host " G5R6 DRIVE-PROOF EXPORT: PASS"
Write-Host "===================================================="
Write-Host "Capture       : $CaptureId"
Write-Host "Events        : $($lines.Count)"
Write-Host "Session       : $sessionId"
Write-Host "Route         : $routeId"
Write-Host "Native accepts: $nativeAcceptedCount"
Write-Host "SHA256        : $sha"
Write-Host "JSONL         : $outputPath"
Write-Host "stderr        : $errorPath"
Write-Host "Mutation      : NONE"
Write-Host "===================================================="
