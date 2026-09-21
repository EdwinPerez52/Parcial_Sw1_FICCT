$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
corepack pnpm --filter @modeler/web test
corepack pnpm --filter @modeler/web build
Push-Location backend_parcial
try { .\mvnw.cmd verify } finally { Pop-Location }
if (Test-Path scripts\local-agent\node_modules) {
  Push-Location scripts\local-agent
  try { npm test } finally { Pop-Location }
}
if (Get-Command flutter -ErrorAction SilentlyContinue) {
  flutter doctor
  if (Test-Path mobile_parcial\pubspec.yaml) {
    Push-Location mobile_parcial
    try { flutter analyze; flutter test } finally { Pop-Location }
  } else {
    Write-Host 'mobile_parcial aún no contiene una aplicación; el generador Flutter corresponde a los incrementos 16-19.'
  }
} else {
  Write-Warning 'Flutter no instalado: se omite la validación móvil.'
}
