$ErrorActionPreference = 'Stop'
if (-not (Get-Command flutter -ErrorAction SilentlyContinue)) { throw 'Flutter SDK no está en PATH.' }
flutter doctor -v
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'ADB no está en PATH. Agrega Android SDK platform-tools.' }
Write-Host 'Dispositivos Android detectados:'
adb devices -l
if (-not ((adb devices) -match '\tdevice$')) {
  throw 'No hay un Android autorizado. Desbloquea el Samsung A56, activa Depuración USB y acepta la huella RSA.'
}
