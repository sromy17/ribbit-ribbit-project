param(
    [string]$EnvFilePath = "",
    [switch]$LoadOnly,
    [string]$MavenGoal = "spring-boot:run"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Info([string]$Message) {
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Get-RepositoryRoot() {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Get-RedactedValue([string]$Name, [string]$Value) {
    if ($Name -match "PASSWORD|SECRET|TOKEN") {
        if ([string]::IsNullOrEmpty($Value)) {
            return "<empty>"
        }
        return "<set>"
    }
    return $Value
}

function Import-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }

    $loaded = @()
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed -split "=", 2
        if ($parts.Length -ne 2) {
            continue
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        $loaded += $name
    }

    return $loaded
}

try {
    $repositoryRoot = Get-RepositoryRoot
    if ([string]::IsNullOrWhiteSpace($EnvFilePath)) {
        $EnvFilePath = Join-Path $repositoryRoot ".env"
    } elseif (-not [System.IO.Path]::IsPathRooted($EnvFilePath)) {
        $EnvFilePath = Join-Path $repositoryRoot $EnvFilePath
    }
    $EnvFilePath = (Resolve-Path -LiteralPath $EnvFilePath).Path

    Write-Info "Repository root: $repositoryRoot"
    Write-Info "Loading environment variables from $EnvFilePath"
    $loadedVars = Import-EnvFile -Path $EnvFilePath
    Write-Pass "Loaded $($loadedVars.Count) variable(s) into this process."

    $required = @(
        "SPRING_PROFILES_ACTIVE",
        "SERVER_PORT",
        "DB_URL",
        "DB_USER",
        "DB_PASSWORD",
        "DB_OLTP_URL",
        "DB_OLTP_USER",
        "DB_OLTP_PASSWORD",
        "DB_OLAP_URL",
        "DB_OLAP_USER",
        "DB_OLAP_PASSWORD",
        "ETL_ENABLED",
        "ETL_FIXED_DELAY_MS"
    )
    $missing = @()

    foreach ($varName in $required) {
        $value = [Environment]::GetEnvironmentVariable($varName, "Process")
        if ([string]::IsNullOrWhiteSpace($value)) {
            $missing += $varName
        }
    }

    if ($missing.Count -gt 0) {
        Write-Fail "Missing required variable(s): $($missing -join ', ')"
        exit 1
    }

    Write-Pass "Required variables are present."

    $expectedValues = @{
        "DB_URL" = "jdbc:postgresql://localhost:5432/ribbit_oltp"
        "DB_OLTP_URL" = "jdbc:postgresql://localhost:5432/ribbit_oltp"
        "DB_OLAP_URL" = "jdbc:postgresql://localhost:5432/ribbit_olap"
    }
    foreach ($name in $expectedValues.Keys) {
        $actual = [Environment]::GetEnvironmentVariable($name, "Process")
        if ($actual -ne $expectedValues[$name]) {
            throw "$name is '$actual'; expected '$($expectedValues[$name])'."
        }
    }

    Write-Info "Effective database configuration:"
    foreach ($name in @("DB_URL", "DB_USER", "DB_OLTP_URL", "DB_OLTP_USER", "DB_OLAP_URL", "DB_OLAP_USER", "ETL_ENABLED", "ETL_FIXED_DELAY_MS", "SERVER_PORT")) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        Write-Host ("  {0}={1}" -f $name, (Get-RedactedValue $name $value))
    }
    Write-Pass "OLTP and OLAP variables point to the expected local databases."

    if ($LoadOnly) {
        Write-Info "Load-only mode complete. Maven was not started."
        exit 0
    }

    Push-Location $repositoryRoot
    try {
        Write-Info "Starting application from: $repositoryRoot"
        Write-Info "Starting application with: mvn $MavenGoal"
        mvn $MavenGoal
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
} catch {
    Write-Fail $_.Exception.Message
    exit 1
}
