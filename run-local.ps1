$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path $envFile)) {
    throw "Missing .env file at $envFile."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line -split "=", 2
        if ($parts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0])) {
            throw "Invalid .env entry: $line"
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$requiredVariables = @(
    "DB_OLTP_URL",
    "DB_OLAP_URL",
    "DB_USER",
    "DB_PASSWORD",
    "DB_OLAP_USER",
    "DB_OLAP_PASSWORD",
    "SERVER_PORT"
)

foreach ($variable in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace((Get-Item "Env:$variable").Value)) {
        throw "Required environment variable '$variable' is not set in .env."
    }
}

mvn spring-boot:run
