$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "==> Generando aplicación Flutter en mobile_parcial..." -ForegroundColor Cyan
Push-Location backend_parcial
try {
    .\mvnw.cmd test -Dtest=FlutterGeneratorTest#generatesCanonicalFlutterAppInMobileParcial
} finally {
    Pop-Location
}

if (Get-Command flutter -ErrorAction SilentlyContinue) {
    Write-Host "==> Obteniendo dependencias y verificando plataformas..." -ForegroundColor Cyan
    Push-Location mobile_parcial
    try {
        if (-not (Test-Path android)) {
            flutter create . --platforms=android,web
        }
        flutter pub get
        flutter analyze --no-fatal-infos
    } finally {
        Pop-Location
    }
    Write-Host "==> Aplicación Flutter generada y verificada exitosamente en mobile_parcial." -ForegroundColor Green
} else {
    Write-Host "==> Aplicación Flutter generada en mobile_parcial (Flutter no encontrado en PATH para pub get)." -ForegroundColor Yellow
}
