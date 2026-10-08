param(
    [string]$EnvFilePath = ".env",
    [string]$BaseUrl,
    [switch]$SkipAuthFlow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Write-Info([string]$Message) {
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Load-EnvFile([string]$Path) {
    $resolvedPath = $Path

    if (-not [System.IO.Path]::IsPathRooted($resolvedPath)) {
        $candidateFromCwd = Join-Path -Path (Get-Location) -ChildPath $resolvedPath
        if (Test-Path -LiteralPath $candidateFromCwd) {
            $resolvedPath = $candidateFromCwd
        } else {
            $candidateFromRepoRoot = Join-Path -Path (Split-Path -Parent $PSScriptRoot) -ChildPath $resolvedPath
            if (Test-Path -LiteralPath $candidateFromRepoRoot) {
                $resolvedPath = $candidateFromRepoRoot
            }
        }
    }

    if (-not (Test-Path -LiteralPath $resolvedPath)) {
        Write-Info "No env file found at '$Path'. Using existing process environment variables."
        return
    }

    Write-Info "Loading environment variables from '$resolvedPath'."

    foreach ($line in Get-Content -LiteralPath $resolvedPath) {
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

        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-StatusCodeForGet([string]$Uri, [hashtable]$Headers = $null) {
    try {
        if ($null -eq $Headers) {
            $response = Invoke-WebRequest -Method Get -Uri $Uri
        } else {
            $response = Invoke-WebRequest -Method Get -Uri $Uri -Headers $Headers
        }
        return [int]$response.StatusCode
    } catch [System.Net.WebException] {
        if ($null -ne $_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

Load-EnvFile -Path $EnvFilePath

if (-not $BaseUrl) {
    if ($env:APP_BASE_URL) {
        $BaseUrl = $env:APP_BASE_URL
    } elseif ($env:SERVER_PORT) {
        $BaseUrl = "http://localhost:$($env:SERVER_PORT)"
    } else {
        $BaseUrl = "http://localhost:8081"
    }
}

$BaseUrl = $BaseUrl.TrimEnd('/')

$requiredVars = @(
    "SUPABASE_ISSUER_URI",
    "SUPABASE_JWK_SET_URI",
    "SUPABASE_AUDIENCE"
)

$missingVars = @()
foreach ($name in $requiredVars) {
    if (-not [System.Environment]::GetEnvironmentVariable($name, "Process")) {
        $missingVars += $name
    }
}

if ($missingVars.Count -gt 0) {
    Write-Fail "Missing required environment variables: $($missingVars -join ', ')"
    exit 1
}

Write-Info "Using API base URL: $BaseUrl"
Write-Info "Supabase issuer: $($env:SUPABASE_ISSUER_URI)"
Write-Info "Supabase JWKS URI: $($env:SUPABASE_JWK_SET_URI)"

$failed = 0

try {
    $jwks = Invoke-RestMethod -Method Get -Uri $env:SUPABASE_JWK_SET_URI
    if ($null -eq $jwks -or $null -eq $jwks.keys -or $jwks.keys.Count -lt 1) {
        Write-Fail "JWKS endpoint responded, but no signing keys were found."
        $failed++
    } else {
        Write-Pass "JWKS endpoint reachable and returned $($jwks.keys.Count) key(s)."
    }
} catch {
    Write-Fail "Failed to call JWKS endpoint: $($_.Exception.Message)"
    $failed++
}

$protectedUri = "$BaseUrl/accounts/1"
try {
    $status = Get-StatusCodeForGet -Uri $protectedUri
    if ($status -eq 401 -or $status -eq 403) {
        Write-Pass "Protected endpoint rejects anonymous requests with status $status."
    } else {
        Write-Fail "Expected 401 or 403 for anonymous request, but got status $status."
        $failed++
    }
} catch {
    Write-Fail "Failed to call backend endpoint '$protectedUri': $($_.Exception.Message)"
    Write-Info "Make sure the Spring Boot app is running before executing this script."
    $failed++
}

$hasAuthVars = $env:SUPABASE_ANON_KEY -and $env:SUPABASE_TEST_EMAIL -and $env:SUPABASE_TEST_PASSWORD

if ($SkipAuthFlow) {
    Write-Info "Skipping token sign-in flow because -SkipAuthFlow was provided."
} elseif (-not $hasAuthVars) {
    Write-Info "Skipping token sign-in flow. Set SUPABASE_ANON_KEY, SUPABASE_TEST_EMAIL, and SUPABASE_TEST_PASSWORD to enable it."
} else {
    try {
        $tokenUri = "$($env:SUPABASE_ISSUER_URI.TrimEnd('/'))/token?grant_type=password"
        $headers = @{
            "apikey" = $env:SUPABASE_ANON_KEY
            "Content-Type" = "application/json"
        }
        $body = @{
            email = $env:SUPABASE_TEST_EMAIL
            password = $env:SUPABASE_TEST_PASSWORD
        } | ConvertTo-Json

        $tokenResponse = Invoke-RestMethod -Method Post -Uri $tokenUri -Headers $headers -Body $body
        $accessToken = $tokenResponse.access_token

        if (-not $accessToken) {
            Write-Fail "Supabase token response did not include access_token."
            $failed++
        } else {
            Write-Pass "Supabase returned an access token."

            $authHeaders = @{ "Authorization" = "Bearer $accessToken" }
            $authStatus = Get-StatusCodeForGet -Uri $protectedUri -Headers $authHeaders

            if ($authStatus -eq 401 -or $authStatus -eq 403) {
                Write-Fail "Authenticated request still returned status $authStatus. Check issuer, audience, and JWKS values."
                $failed++
            } else {
                Write-Pass "Authenticated request accepted (status $authStatus)."
            }
        }
    } catch {
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            Write-Fail "Token sign-in flow failed: $($_.Exception.Message) | Details: $($_.ErrorDetails.Message)"
        } else {
            Write-Fail "Token sign-in flow failed: $($_.Exception.Message)"
        }
        $failed++
    }
}

if ($failed -gt 0) {
    Write-Host "`nCompleted with $failed failing check(s)." -ForegroundColor Red
    exit 1
}

Write-Host "`nAll Supabase connectivity checks passed." -ForegroundColor Green
exit 0
