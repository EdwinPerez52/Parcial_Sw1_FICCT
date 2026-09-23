$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
pnpm --filter @modeler/web test
pnpm --filter @modeler/web build
Push-Location Backend-web
try { .\mvnw.cmd verify } finally { Pop-Location }
if (Test-Path mobile-flutter\tools\local-agent\node_modules) {
  Push-Location mobile-flutter\tools\local-agent
  try { npm test } finally { Pop-Location }
}
if (Get-Command flutter -ErrorAction SilentlyContinue) {
  flutter doctor
  if (Test-Path mobile-flutter\pubspec.yaml) {
    Push-Location mobile-flutter
    try { flutter analyze; flutter test } finally { Pop-Location }
  } else {
    Write-Host 'mobile-flutter aún no contiene una aplicación; el generador Flutter corresponde a los incrementos 16-19.'
  }
} else {
  Write-Warning 'Flutter no instalado: se omite la validación móvil.'
}
