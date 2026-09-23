$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot ".env"

if (Test-Path -LiteralPath $environmentFile) {
    foreach ($rawLine in Get-Content -LiteralPath $environmentFile) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }

        $parts = $line.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        if ($name -match '^[A-Za-z_][A-Za-z0-9_]*$' -and
            [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, "Process"))) {
            Set-Item -LiteralPath "Env:$name" -Value $value
        }
    }
}

$env:SPRING_PROFILES_ACTIVE = "dev"
Set-Location (Join-Path $repositoryRoot "Backend-web")
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
