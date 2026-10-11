param(
    [string] $Serial =
        "62030DLCH0014M"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repo =
    "C:\GitHub\Routenplanung\routing-platform-foundation-0.2"

$expectedHead =
    "66c6a2b1a18b626130650116d0391fcbd0e38db0"

$package =
    "org.routingplatform.app"

$component =
    "org.routingplatform.app/.MainActivity"

$deviceRoutePort =
    8787

$faultPort =
    18787

$faultHostTarget =
    "tcp:$faultPort"

$tools =
    Join-Path `
        $repo `
        "tools"

$harness =
    Join-Path `
        $tools `
        "navigation_device_harness.ps1"

$faultService =
    Join-Path `
        $tools `
        "navigation_fault_injection_service.py"

$apk =
    Join-Path `
        $repo `
        "platform\android\app\build\outputs\apk\debug\app-debug.apk"

if (
    -not (
        Test-Path -LiteralPath $harness
    )
) {
    throw "Device harness is missing: $harness"
}

. $harness

function Get-ChangedPaths {
    $lines =
        @(
            git `
                -C $repo `
                -c core.quotepath=false `
                status `
                --porcelain=v1 `
                --untracked-files=all
        )

    if (
        $LASTEXITCODE -ne
            0
    ) {
        throw "git status failed."
    }

    return @(
        $lines |
        ForEach-Object {
            if (
                $_.Length -lt
                    4
            ) {
                throw "Unexpected git status line: $_"
            }

            $_.Substring(
                3
            ).Trim()
        } |
        Sort-Object -Unique
    )
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Expected,

        [Parameter(Mandatory = $true)]
        [string[]] $Actual,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $difference =
        @(
            Compare-Object `
                -ReferenceObject $Expected `
                -DifferenceObject $Actual
        )

    if (
        @($difference).Count -ne
            0
    ) {
        $difference |
            Format-Table -AutoSize |
            Out-Host

        throw "$Label mismatch."
    }
}

function Wait-HostEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Uri,

        [int] $TimeoutSeconds =
            10
    )

    $deadline =
        (Get-Date).AddSeconds(
            $TimeoutSeconds
        )

    $lastError =
        $null

    while (
        (Get-Date) -lt
            $deadline
    ) {
        try {
            return Invoke-RestMethod `
                -UseBasicParsing `
                -Uri $Uri `
                -Method Get `
                -TimeoutSec 2
        } catch {
            $lastError =
                $_.Exception.Message

            Start-Sleep `
                -Milliseconds 250
        }
    }

    $lastErrorText =
        "<none>"

    if (
        $null -ne $lastError
    ) {
        $lastErrorText =
            $lastError
    }

    throw (
        "Host endpoint did not become ready: {0}. Last error: {1}" -f
            $Uri,
            $lastErrorText
    )
}

function Assert-HostTcpPortFree {
    param(
        [Parameter(Mandatory = $true)]
        [int] $Port
    )

    $listener =
        $null

    try {
        $listener =
            [System.Net.Sockets.TcpListener]::new(
                [System.Net.IPAddress]::Loopback,
                $Port
            )

        $listener.Start()
    } catch {
        throw (
            "Host TCP port {0} is already occupied: {1}" -f
                $Port,
                $_.Exception.Message
        )
    } finally {
        if (
            $null -ne $listener
        ) {
            $listener.Stop()
        }
    }
}

function Set-FaultMode {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Mode
    )

    $body =
        @{
            mode =
                $Mode

            resetStats =
                $true
        } |
        ConvertTo-Json `
            -Compress

    Invoke-RestMethod `
        -UseBasicParsing `
        -Uri (
            "http://127.0.0.1:{0}/__test__/configure" -f
                $faultPort
        ) `
        -Method Post `
        -TimeoutSec 2 `
        -Headers @{
            "X-Routing-Platform-Test-Control" =
                "1"
        } `
        -ContentType "application/json" `
        -Body $body |
        Out-Null
}

function Get-FaultStats {
    return Invoke-RestMethod `
        -UseBasicParsing `
        -Uri (
            "http://127.0.0.1:{0}/__test__/stats" -f
                $faultPort
        ) `
        -Method Get `
        -TimeoutSec 2 `
        -Headers @{
            "X-Routing-Platform-Test-Control" =
                "1"
        }
}

function Assert-PreviewFallbackUsable {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb
    )

    Wait-RoutingPlatformUiNode `
        -Adb $Adb `
        -Serial $Serial `
        -ResourceId "rp.navigation.session.Preview" `
        -TimeoutSeconds 15 |
        Out-Null

    $primary =
        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId "rp.navigation.primary_action" `
            -TimeoutSeconds 15

    Assert-RoutingPlatformUiNodeEnabled `
        -Node $primary.Node `
        -Label "Preview primary navigation control"

    Assert-RoutingPlatformProcessAlive `
        -Adb $Adb `
        -Serial $Serial `
        -Package $package
}

function Invoke-FaultCase {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Mode,

        [Parameter(Mandatory = $true)]
        [string] $ExpectedAcquisitionId,

        [Parameter(Mandatory = $true)]
        [int] $ExpectedRouteRequests
    )

    Write-Host
    Write-Host (
        "===== FAULT CASE: {0} =====" -f
            $Mode
    )

    try {
        Set-FaultMode `
            -Mode $Mode

        Set-RoutingPlatformReverse `
            -Adb $Adb `
            -Serial $Serial `
            -DevicePort $deviceRoutePort `
            -HostTarget $faultHostTarget

        Start-RoutingPlatformApp `
            -Adb $Adb `
            -Serial $Serial `
            -Package $package `
            -Component $component

        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId "rp.navigation.root" `
            -TimeoutSeconds 15 |
            Out-Null

        Wait-RoutingPlatformUiNode `
            -Adb $Adb `
            -Serial $Serial `
            -ResourceId $ExpectedAcquisitionId `
            -TimeoutSeconds 20 |
            Out-Null

        Assert-PreviewFallbackUsable `
            -Adb $Adb

        $stats =
            Get-FaultStats

        if (
            [int]$stats.routeRequests -ne
                $ExpectedRouteRequests
        ) {
            throw (
                "Unexpected route request count for {0}. Expected {1}, actual {2}" -f
                    $Mode,
                    $ExpectedRouteRequests,
                    $stats.routeRequests
            )
        }

        Write-Host (
            "PASS {0}: acquisition={1}, requests={2}" -f
                $Mode,
                $ExpectedAcquisitionId,
                $stats.routeRequests
        )
    } catch {
        $evidence =
            Save-RoutingPlatformEvidence `
                -Adb $Adb `
                -Serial $Serial `
                -Package $package `
                -Repo $repo `
                -CaseName $Mode

        Write-Host "Evidence: $evidence"

        throw
    }
}

Write-Host "===================================================="
Write-Host " G5R4 PIXEL FAULT-INJECTION PROOF"
Write-Host " RESOURCE-ID ANCHORS / NO SCREEN COORDINATES"
Write-Host "===================================================="

$expectedChanged =
    @(
        @(
            "platform/android/app/src/main/java/org/routingplatform/app/MainActivity.kt"
            "platform/android/app/src/main/java/org/routingplatform/app/ui/NavigationScreen.kt"
            "platform/android/app/src/main/java/org/routingplatform/app/ui/NavigationUiTestTags.kt"
            "platform/android/app/src/test/java/org/routingplatform/app/ui/NavigationUiTestTagsTest.kt"
            "tools/navigation_device_harness.ps1"
            "tools/navigation_fault_injection_service.py"
            "tools/navigation_g5r4_device_proof.ps1"
            "tools/test_navigation_fault_injection_service.py"
        ) |
        Sort-Object
    )

$branch =
    (
        git `
            -C $repo `
            branch `
            --show-current
    ).Trim()

if (
    $LASTEXITCODE -ne
        0
) {
    throw "git branch failed."
}

if (
    $branch -ne
        "main"
) {
    throw "Unexpected branch: $branch"
}

$head =
    (
        git `
            -C $repo `
            rev-parse `
            HEAD
    ).Trim()

if (
    $LASTEXITCODE -ne
        0
) {
    throw "git rev-parse failed."
}

if (
    $head -ne
        $expectedHead
) {
    throw (
        "Unexpected G5R4 proof HEAD.`nExpected: {0}`nActual  : {1}" -f
            $expectedHead,
            $head
    )
}

$changed =
    @(
        Get-ChangedPaths
    )

Assert-ExactSet `
    -Expected $expectedChanged `
    -Actual $changed `
    -Label "G5R4 proof dirty scope"

$staged =
    @(
        git `
            -C $repo `
            diff `
            --cached `
            --name-only
    )

if (
    $LASTEXITCODE -ne
        0
) {
    throw "git diff --cached failed."
}

if (
    @($staged).Count -ne
        0
) {
    throw "G5R4 device proof requires an empty index."
}

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

$python =
    Resolve-RoutingPlatformPython

$logRootBase =
    Join-Path `
        $env:TEMP `
        "routing-platform-g5r4-fault-service"

$runStamp =
    Get-Date -Format "yyyyMMdd-HHmmssfff"

$logRoot =
    Join-Path `
        $logRootBase `
        $runStamp

New-Item `
    -ItemType Directory `
    -Path $logRoot `
    -Force |
    Out-Null

$stdout =
    Join-Path `
        $logRoot `
        "stdout.txt"

$stderr =
    Join-Path `
        $logRoot `
        "stderr.txt"

$processArguments =
    @()

$processArguments +=
    @(
        $python.PrefixArguments
    )

$processArguments +=
    @(
        $faultService,
        "--listen",
        "127.0.0.1",
        "--port",
        $faultPort.ToString()
    )

$originalReverse =
    Get-RoutingPlatformReverseTarget `
        -Adb $adb `
        -Serial $Serial `
        -DevicePort $deviceRoutePort

Assert-HostTcpPortFree `
    -Port $faultPort

$faultProcess =
    $null

$proofFailure =
    $null

$cleanupFailures =
    @()

try {
    $faultProcess =
        Start-Process `
            -FilePath $python.Path `
            -ArgumentList $processArguments `
            -PassThru `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr

    Wait-HostEndpoint `
        -Uri (
            "http://127.0.0.1:{0}/health" -f
                $faultPort
        ) `
        -TimeoutSeconds 10 |
        Out-Null

    if (
        $faultProcess.HasExited
    ) {
        throw (
            "Owned fault-injection service exited unexpectedly with code {0}." -f
                $faultProcess.ExitCode
        )
    }

    Write-Host
    Write-Host "===== TRANSPORT LOSS ====="

    try {
        Remove-RoutingPlatformReverse `
            -Adb $adb `
            -Serial $Serial `
            -DevicePort $deviceRoutePort

        Start-RoutingPlatformApp `
            -Adb $adb `
            -Serial $Serial `
            -Package $package `
            -Component $component

        Wait-RoutingPlatformUiNode `
            -Adb $adb `
            -Serial $Serial `
            -ResourceId "rp.navigation.acquisition.LiveFailed.TransportUnavailable" `
            -TimeoutSeconds 20 |
            Out-Null

        Assert-PreviewFallbackUsable `
            -Adb $adb

        Write-Host "PASS transport loss: fail-closed fallback remains usable"
    } catch {
        $evidence =
            Save-RoutingPlatformEvidence `
                -Adb $adb `
                -Serial $Serial `
                -Package $package `
                -Repo $repo `
                -CaseName "transport-loss"

        Write-Host "Evidence: $evidence"

        throw
    }

    Invoke-FaultCase `
        -Adb $adb `
        -Mode "backend_failure" `
        -ExpectedAcquisitionId "rp.navigation.acquisition.LiveFailed.ServiceUnavailable" `
        -ExpectedRouteRequests 2

    Invoke-FaultCase `
        -Adb $adb `
        -Mode "routing_timeout" `
        -ExpectedAcquisitionId "rp.navigation.acquisition.LiveFailed.Timeout" `
        -ExpectedRouteRequests 2

    Invoke-FaultCase `
        -Adb $adb `
        -Mode "no_suitable_edges" `
        -ExpectedAcquisitionId "rp.navigation.acquisition.LiveFailed.NoSuitableEdges" `
        -ExpectedRouteRequests 1

    Invoke-FaultCase `
        -Adb $adb `
        -Mode "invalid_response" `
        -ExpectedAcquisitionId "rp.navigation.acquisition.LiveFailed.InvalidResponse" `
        -ExpectedRouteRequests 1

    Invoke-FaultCase `
        -Adb $adb `
        -Mode "unknown_retryable" `
        -ExpectedAcquisitionId "rp.navigation.acquisition.LiveFailed.InvalidRequest" `
        -ExpectedRouteRequests 1
} catch {
    $proofFailure =
        $_
}
finally {
    try {
        Restore-RoutingPlatformReverse `
            -Adb $adb `
            -Serial $Serial `
            -DevicePort $deviceRoutePort `
            -OriginalTarget $originalReverse
    } catch {
        $cleanupFailures +=
            (
                "ADB reverse restore failed: {0}" -f
                    $_.Exception.Message
            )
    }

    try {
        if (
            $null -ne $faultProcess -and
            -not $faultProcess.HasExited
        ) {
            Stop-Process `
                -Id $faultProcess.Id `
                -Force

            $faultProcess.WaitForExit(
                5000
            )

            if (
                -not $faultProcess.HasExited
            ) {
                throw "Fault-injection service did not exit after Stop-Process."
            }
        }
    } catch {
        $cleanupFailures +=
            (
                "Fault-service cleanup failed: {0}" -f
                    $_.Exception.Message
            )
    }
}

if (
    @($cleanupFailures).Count -ne
        0
) {
    Write-Warning (
        "G5R4 cleanup incident(s): " +
            (
                $cleanupFailures -join
                    " | "
            )
    )
}

if (
    $null -ne $proofFailure
) {
    throw $proofFailure
}

if (
    @($cleanupFailures).Count -ne
        0
) {
    throw (
        "G5R4 proof succeeded but cleanup did not complete: " +
            (
                $cleanupFailures -join
                    " | "
            )
    )
}

$finalChanged =
    @(
        Get-ChangedPaths
    )

Assert-ExactSet `
    -Expected $expectedChanged `
    -Actual $finalChanged `
    -Label "Final G5R4 proof dirty scope"

$finalStaged =
    @(
        git `
            -C $repo `
            diff `
            --cached `
            --name-only
    )

if (
    $LASTEXITCODE -ne
        0
) {
    throw "final git diff --cached failed."
}

if (
    @($finalStaged).Count -ne
        0
) {
    throw "Files became staged during G5R4 device proof."
}

Write-Host
Write-Host "===================================================="
Write-Host " G5R4 PIXEL FAULT-INJECTION PROOF: PASS"
Write-Host "===================================================="
Write-Host "UI anchors             : RESOURCE-ID / NON-LOCALIZED"
Write-Host "Hardcoded screen taps  : NONE"
Write-Host "Transport loss         : FAIL CLOSED / FALLBACK USABLE"
Write-Host "Backend 502            : EXACTLY 1 RETRY"
Write-Host "Structured timeout     : EXACTLY 1 RETRY"
Write-Host "No suitable edges      : NO RETRY"
Write-Host "Invalid route response : NO RETRY"
Write-Host "Unknown retryable code : LOCAL POLICY WINS / NO RETRY"
Write-Host "App process            : STABLE"
Write-Host "ADB reverse            : ORIGINAL MAPPING RESTORED"
Write-Host "Repo dirty scope       : EXACTLY 8"
Write-Host "Staged                 : NONE"
Write-Host "Next                   : G5R4 SEAL"
Write-Host "===================================================="