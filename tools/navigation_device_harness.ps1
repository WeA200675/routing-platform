Set-StrictMode -Version Latest

function Resolve-RoutingPlatformAdb {
    $sdkAdb =
        Join-Path `
            $env:LOCALAPPDATA `
            "Android\Sdk\platform-tools\adb.exe"

    if (
        Test-Path -LiteralPath $sdkAdb
    ) {
        return $sdkAdb
    }

    $command =
        Get-Command `
            adb.exe `
            -ErrorAction SilentlyContinue

    if (
        $null -eq $command
    ) {
        throw "adb.exe was not found."
    }

    return $command.Source
}

function Resolve-RoutingPlatformPython {
    $python =
        Get-Command `
            python.exe `
            -ErrorAction SilentlyContinue

    if (
        $null -ne $python
    ) {
        return [pscustomobject]@{
            Path = $python.Source
            PrefixArguments = @()
        }
    }

    $py =
        Get-Command `
            py.exe `
            -ErrorAction SilentlyContinue

    if (
        $null -eq $py
    ) {
        throw "Python 3 executable was not found."
    }

    return [pscustomobject]@{
        Path = $py.Source
        PrefixArguments = @("-3")
    }
}

function Invoke-RoutingPlatformAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string[]] $CommandArguments,

        [switch] $AllowFailure,

        [switch] $IncludeStandardError
    )

    $rawOutput =
        @()

    $exitCode =
        -1

    # Windows PowerShell 5.1 can represent redirected native stderr
    # as ErrorRecord objects. adb also writes normal status output
    # to stderr on successful commands such as adb pull.
    $previousErrorActionPreference =
        $ErrorActionPreference

    try {
        $ErrorActionPreference =
            "Continue"

        $rawOutput =
            @(
                & $Adb `
                    -s $Serial `
                    @CommandArguments `
                    2>&1
            )

        $exitCode =
            $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference =
            $previousErrorActionPreference
    }

    $standardOutput =
        @()

    $standardError =
        @()

    foreach ($item in $rawOutput) {
        if (
            $item -is
                [System.Management.Automation.ErrorRecord]
        ) {
            $standardError +=
                $item.ToString()
        } else {
            $standardOutput +=
                $item.ToString()
        }
    }

    if (
        !$AllowFailure -and
        $exitCode -ne 0
    ) {
        $diagnostic =
            @(
                $standardOutput
                $standardError
            ) -join
                "`n"

        throw (
            "adb failed ({0}): {1}" -f
                $exitCode,
                $diagnostic
        )
    }

    if (
        $IncludeStandardError
    ) {
        return @(
            $standardOutput
            $standardError
        )
    }

    return @(
        $standardOutput
    )
}

function Assert-RoutingPlatformDevice {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial
    )

    $state =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "get-state"
                )
        )

    if (
        (
            $state -join
                ""
        ).Trim() -ne
            "device"
    ) {
        throw "Android device is not online: $Serial"
    }
}


function Normalize-RoutingPlatformSystemUi {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial
    )

    # Test infrastructure only: collapse transient SystemUI overlays
    # such as NotificationShade before reading app semantics.
    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "cmd",
            "statusbar",
            "collapse"
        ) `
        -AllowFailure |
        Out-Null

    Start-Sleep `
        -Milliseconds 150
}

function Get-RoutingPlatformReverseTarget {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [int] $DevicePort
    )

    $lines =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "reverse",
                    "--list"
                )
        )

    $needle =
        "tcp:$DevicePort"

    foreach ($line in $lines) {
        $parts =
            @(
                (
                    $line.ToString().Trim()
                ) -split
                    "\s+"
            )

        if (
            @($parts).Count -lt 2
        ) {
            continue
        }

        for (
            $index = 0;
            $index -lt
                @($parts).Count - 1;
            $index += 1
        ) {
            if (
                $parts[$index] -eq
                    $needle
            ) {
                return $parts[
                    $index + 1
                ]
            }
        }
    }

    return $null
}

function Remove-RoutingPlatformReverse {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [int] $DevicePort
    )

    $existing =
        Get-RoutingPlatformReverseTarget `
            -Adb $Adb `
            -Serial $Serial `
            -DevicePort $DevicePort

    if (
        $null -eq $existing
    ) {
        return
    }

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "reverse",
            "--remove",
            "tcp:$DevicePort"
        ) |
        Out-Null

    $after =
        Get-RoutingPlatformReverseTarget `
            -Adb $Adb `
            -Serial $Serial `
            -DevicePort $DevicePort

    if (
        $null -ne $after
    ) {
        throw "ADB reverse mapping was not removed."
    }
}

function Set-RoutingPlatformReverse {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [int] $DevicePort,

        [Parameter(Mandatory = $true)]
        [string] $HostTarget
    )

    Remove-RoutingPlatformReverse `
        -Adb $Adb `
        -Serial $Serial `
        -DevicePort $DevicePort

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "reverse",
            "tcp:$DevicePort",
            $HostTarget
        ) |
        Out-Null

    $actual =
        Get-RoutingPlatformReverseTarget `
            -Adb $Adb `
            -Serial $Serial `
            -DevicePort $DevicePort

    if (
        $actual -ne
            $HostTarget
    ) {
        $actualText =
            "<none>"

        if (
            $null -ne $actual
        ) {
            $actualText =
                $actual
        }

        throw (
            "ADB reverse verification failed. Expected {0}, actual {1}" -f
                $HostTarget,
                $actualText
        )
    }
}

function Restore-RoutingPlatformReverse {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [int] $DevicePort,

        [AllowNull()]
        [string] $OriginalTarget
    )

    if (
        [string]::IsNullOrWhiteSpace(
            $OriginalTarget
        )
    ) {
        Remove-RoutingPlatformReverse `
            -Adb $Adb `
            -Serial $Serial `
            -DevicePort $DevicePort

        return
    }

    Set-RoutingPlatformReverse `
        -Adb $Adb `
        -Serial $Serial `
        -DevicePort $DevicePort `
        -HostTarget $OriginalTarget
}

function Start-RoutingPlatformApp {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string] $Package,

        [Parameter(Mandatory = $true)]
        [string] $Component
    )

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
            "force-stop",
            $Package
        ) |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            $Component
        ) |
        Out-Null

    Normalize-RoutingPlatformSystemUi `
        -Adb $Adb `
        -Serial $Serial
}

function Get-RoutingPlatformUiXml {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial
    )

    Normalize-RoutingPlatformSystemUi `
        -Adb $Adb `
        -Serial $Serial

    $remote =
        "/sdcard/routing-platform-ui.xml"

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "uiautomator",
            "dump",
            $remote
        ) |
        Out-Null

    $lines =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "exec-out",
                    "cat",
                    $remote
                )
        )

    $text =
        $lines -join
            "`n"

    if (
        [string]::IsNullOrWhiteSpace(
            $text
        )
    ) {
        throw "UIAutomator returned an empty hierarchy."
    }

    return [xml]$text
}

function Get-RoutingPlatformUiNodesByResourceId {
    param(
        [Parameter(Mandatory = $true)]
        [xml] $Xml,

        [Parameter(Mandatory = $true)]
        [string] $ResourceId
    )

    $allNodes =
        @(
            $Xml.SelectNodes(
                "//node"
            )
        )

    return @(
        $allNodes |
        Where-Object {
            $_.GetAttribute(
                "resource-id"
            ) -eq
                $ResourceId
        }
    )
}

function Wait-RoutingPlatformUiNode {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string] $ResourceId,

        [int] $TimeoutSeconds =
            20
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
            $xml =
                Get-RoutingPlatformUiXml `
                    -Adb $Adb `
                    -Serial $Serial

            $nodes =
                @(
                    Get-RoutingPlatformUiNodesByResourceId `
                        -Xml $xml `
                        -ResourceId $ResourceId
                )

            if (
                @($nodes).Count -eq
                    1
            ) {
                return [pscustomobject]@{
                    Xml = $xml
                    Node = $nodes[0]
                }
            }

            if (
                @($nodes).Count -gt
                    1
            ) {
                throw "Resource id is not unique: $ResourceId"
            }
        } catch {
            $lastError =
                $_.Exception.Message
        }

        Start-Sleep `
            -Milliseconds 400
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
        "Timed out waiting for UI resource-id {0}. Last error: {1}" -f
            $ResourceId,
            $lastErrorText
    )
}

function Assert-RoutingPlatformUiNodeEnabled {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement] $Node,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $enabled =
        $Node.GetAttribute(
            "enabled"
        )

    if (
        $enabled -ne
            "true"
    ) {
        throw "$Label is not enabled."
    }
}

function Assert-RoutingPlatformProcessAlive {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string] $Package
    )

    $output =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "shell",
                    "pidof",
                    $Package
                ) `
                -AllowFailure
        )

    if (
        [string]::IsNullOrWhiteSpace(
            (
                $output -join
                    ""
            ).Trim()
        )
    ) {
        throw "Application process is not alive: $Package"
    }
}

function Install-RoutingPlatformApkVerified {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string] $Package,

        [Parameter(Mandatory = $true)]
        [string] $ApkPath
    )

    if (
        -not (
            Test-Path -LiteralPath $ApkPath
        )
    ) {
        throw "APK does not exist: $ApkPath"
    }

    $localHash =
        (
            Get-FileHash `
                -LiteralPath $ApkPath `
                -Algorithm SHA256
        ).Hash

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "install",
            "-r",
            $ApkPath
        ) |
        Out-Null

    $pathLines =
        @(
            Invoke-RoutingPlatformAdb `
                -Adb $Adb `
                -Serial $Serial `
                -CommandArguments @(
                    "shell",
                    "pm",
                    "path",
                    $Package
                )
        )

    $baseCandidates =
        @(
            $pathLines |
            Where-Object {
                $_.ToString().Contains(
                    "base.apk"
                )
            }
        )

    if (
        @($baseCandidates).Count -lt
            1
    ) {
        throw "Installed base.apk path could not be resolved."
    }

    $remoteApkCandidate =
        $baseCandidates[0]

    $remoteApk =
        $remoteApkCandidate.ToString().Trim()

    if (
        -not $remoteApk.StartsWith(
            "package:"
        )
    ) {
        throw "Unexpected pm path output: $remoteApk"
    }

    $remoteApk =
        $remoteApk.Substring(
            "package:".Length
        )

    $proofRoot =
        Join-Path `
            $env:TEMP `
            (
                "routing-platform-apk-proof-" +
                    (
                        Get-Date -Format "yyyyMMdd-HHmmssfff"
                    )
            )

    New-Item `
        -ItemType Directory `
        -Path $proofRoot `
        -Force |
        Out-Null

    $pulledApk =
        Join-Path `
            $proofRoot `
            "base.apk"

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "pull",
            $remoteApk,
            $pulledApk
        ) |
        Out-Null

    $deviceHash =
        (
            Get-FileHash `
                -LiteralPath $pulledApk `
                -Algorithm SHA256
        ).Hash

    if (
        $deviceHash -ne
            $localHash
    ) {
        throw (
            "Installed APK identity mismatch.`nLocal : {0}`nDevice: {1}" -f
                $localHash,
                $deviceHash
        )
    }

    return $localHash
}

function Save-RoutingPlatformEvidence {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Adb,

        [Parameter(Mandatory = $true)]
        [string] $Serial,

        [Parameter(Mandatory = $true)]
        [string] $Package,

        [Parameter(Mandatory = $true)]
        [string] $Repo,

        [Parameter(Mandatory = $true)]
        [string] $CaseName
    )

    $safeCase =
        (
            $CaseName -replace
                "[^A-Za-z0-9_.-]",
                "_"
        )

    $root =
        Join-Path `
            $env:TEMP `
            "routing-platform-g5r4-evidence"

    $directory =
        Join-Path `
            $root `
            (
                (
                    Get-Date -Format "yyyyMMdd-HHmmssfff"
                ) +
                    "-" +
                    $safeCase
            )

    New-Item `
        -ItemType Directory `
        -Path $directory `
        -Force |
        Out-Null

    $remotePng =
        "/sdcard/routing-platform-g5r4-evidence.png"

    $remoteXml =
        "/sdcard/routing-platform-g5r4-evidence.xml"

    $screenPath =
        Join-Path `
            $directory `
            "screen.png"

    $uiPath =
        Join-Path `
            $directory `
            "ui.xml"

    $activityPath =
        Join-Path `
            $directory `
            "activity.txt"

    $logcatPath =
        Join-Path `
            $directory `
            "logcat.txt"

    $reversePath =
        Join-Path `
            $directory `
            "adb-reverse.txt"

    $gitStatusPath =
        Join-Path `
            $directory `
            "git-status.txt"

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "screencap",
            "-p",
            $remotePng
        ) `
        -AllowFailure |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "pull",
            $remotePng,
            $screenPath
        ) `
        -AllowFailure |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "shell",
            "uiautomator",
            "dump",
            $remoteXml
        ) `
        -AllowFailure |
        Out-Null

    Invoke-RoutingPlatformAdb `
        -Adb $Adb `
        -Serial $Serial `
        -CommandArguments @(
            "pull",
            $remoteXml,
            $uiPath
        ) `
        -AllowFailure |
        Out-Null

    @(
        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "shell",
                "dumpsys",
                "activity",
                "activities"
            ) `
            -AllowFailure
    ) |
        Set-Content `
            -LiteralPath $activityPath `
            -Encoding UTF8

    @(
        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "logcat",
                "-d",
                "-v",
                "threadtime"
            ) `
            -AllowFailure
    ) |
        Set-Content `
            -LiteralPath $logcatPath `
            -Encoding UTF8

    @(
        Invoke-RoutingPlatformAdb `
            -Adb $Adb `
            -Serial $Serial `
            -CommandArguments @(
                "reverse",
                "--list"
            ) `
            -AllowFailure
    ) |
        Set-Content `
            -LiteralPath $reversePath `
            -Encoding UTF8

    @(
        & git `
            -C $Repo `
            status `
            --short `
            --untracked-files=all
    ) |
        Set-Content `
            -LiteralPath $gitStatusPath `
            -Encoding UTF8

    return $directory
}