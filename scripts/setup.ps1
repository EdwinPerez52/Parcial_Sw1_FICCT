$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
corepack enable
corepack prepare pnpm@11.19.0 --activate
pnpm install --frozen-lockfile
Push-Location Backend-web
try { .\mvnw.cmd -q dependency:go-offline } finally { Pop-Location }
if (Get-Command flutter -ErrorAction SilentlyContinue) {
  if (Test-Path mobile-flutter\pubspec.yaml) { Push-Location mobile-flutter; try { flutter pub get } finally { Pop-Location } }
} else {
  Write-Warning 'Flutter no está en PATH; consulta README.md antes de trabajar con Android.'
}
