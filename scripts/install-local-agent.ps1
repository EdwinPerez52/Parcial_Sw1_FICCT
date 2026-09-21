$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "==> Verificando Node.js..." -ForegroundColor Cyan
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Error "Node.js no encontrado en PATH. Instala Node.js 22+ y reinicia."
    exit 1
}
$nodeVersion = node --version
Write-Host "    Node.js $nodeVersion" -ForegroundColor Green

Write-Host "==> Instalando dependencias del agente local..." -ForegroundColor Cyan
Push-Location scripts/local-agent
try {
    npm install
    Write-Host "==> Compilando TypeScript..." -ForegroundColor Cyan
    npm run build
    Write-Host "==> Agente local instalado exitosamente." -ForegroundColor Green
    Write-Host ""
    Write-Host "Para iniciar el agente:" -ForegroundColor Yellow
    Write-Host "  pnpm agent:start" -ForegroundColor White
    Write-Host ""
    Write-Host "Para ejecutar pruebas:" -ForegroundColor Yellow
    Write-Host "  pnpm agent:test" -ForegroundColor White
} finally {
    Pop-Location
}
