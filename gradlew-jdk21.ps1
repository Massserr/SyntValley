[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments = @('build')
)

$ErrorActionPreference = 'Stop'

function Test-Java21Home([string] $Candidate) {
    if ([string]::IsNullOrWhiteSpace($Candidate)) {
        return $false
    }

    $releaseFile = Join-Path $Candidate 'release'
    $javaCompiler = Join-Path $Candidate 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $releaseFile) -or -not (Test-Path -LiteralPath $javaCompiler)) {
        return $false
    }

    $release = Get-Content -Raw -LiteralPath $releaseFile -Encoding ascii
    return $release -match 'JAVA_VERSION="21(?:\.|\")'
}

function Find-Java21Home {
    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }

    foreach ($registryRoot in @('HKLM:\SOFTWARE\JavaSoft\JDK', 'HKCU:\SOFTWARE\JavaSoft\JDK')) {
        if (Test-Path $registryRoot) {
            Get-ChildItem $registryRoot |
                Where-Object { $_.PSChildName -like '21*' } |
                Sort-Object PSChildName -Descending |
                ForEach-Object {
                    $javaHome = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).JavaHome
                    if ($javaHome) {
                        $candidates.Add($javaHome)
                    }
                }
        }
    }

    foreach ($knownPath in @(
        "$env:ProgramFiles\Java\latest\jdk-21",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-21*",
        "$env:ProgramFiles\Microsoft\jdk-21*",
        "$env:ProgramFiles\Java\jdk-21*"
    )) {
        Get-Item -Path $knownPath -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Java21Home $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'JDK 21 was not found. Install a 64-bit JDK 21 or set JAVA_HOME to it.'
}

function Get-FreeDriveLetter([string[]] $PreferredLetters) {
    $used = Get-PSDrive -PSProvider FileSystem | Select-Object -ExpandProperty Name
    foreach ($letter in $PreferredLetters) {
        if ($used -notcontains $letter) {
            return $letter
        }
    }

    throw "No free drive letter is available from: $($PreferredLetters -join ', ')"
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleHome = Join-Path $env:USERPROFILE '.gradle'
$java21Home = Find-Java21Home

if (-not (Test-Path -LiteralPath $gradleHome)) {
    New-Item -ItemType Directory -Path $gradleHome | Out-Null
}

$projectDrive = Get-FreeDriveLetter @('S', 'V', 'W', 'X', 'Y')
$cacheDrive = Get-FreeDriveLetter (@('G', 'T', 'U', 'R', 'Q') | Where-Object { $_ -ne $projectDrive })

subst "$projectDrive`:" $projectRoot
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create temporary project drive $projectDrive`:"
}

try {
    subst "$cacheDrive`:" $gradleHome
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create temporary Gradle cache drive $cacheDrive`:"
    }

    try {
        $env:JAVA_HOME = $java21Home
        $env:Path = "$java21Home\bin;$env:Path"
        $env:GRADLE_USER_HOME = "$cacheDrive`:\"

        Write-Host "Using JDK 21: $java21Home"
        Write-Host "Using temporary ASCII paths: $projectDrive`:\ and $cacheDrive`:\"

        Push-Location "$projectDrive`:\"
        try {
            & '.\gradlew.bat' --no-daemon @GradleArguments
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    } finally {
        subst "$cacheDrive`:" /D | Out-Null
    }
} finally {
    subst "$projectDrive`:" /D | Out-Null
}

exit $exitCode
