param(
    [string]$EnvFilePath = ".env",
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
    Write-Info "Loading environment variables from $EnvFilePath"
    $loadedVars = Import-EnvFile -Path $EnvFilePath
    Write-Pass "Loaded $($loadedVars.Count) variable(s) into this process."

    $required = @("SPRING_PROFILES_ACTIVE", "DB_URL", "DB_USER", "DB_PASSWORD")
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

    if ($LoadOnly) {
        Write-Info "Load-only mode complete. Maven was not started."
        exit 0
    }

    Write-Info "Starting application with: mvn $MavenGoal"
    mvn $MavenGoal
    exit $LASTEXITCODE
} catch {
    Write-Fail $_.Exception.Message
    exit 1
}
