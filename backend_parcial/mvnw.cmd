@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%PROJECT_DIR%.mvn\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if exist "%MAVEN_CMD%" goto run
if not exist "%PROJECT_DIR%.mvn\wrapper\dists" mkdir "%PROJECT_DIR%.mvn\wrapper\dists"
echo Maven %MAVEN_VERSION% no esta instalado; descargando la distribucion fijada...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%PROJECT_DIR%.mvn\wrapper\dists\apache-maven.zip'; Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath '%PROJECT_DIR%.mvn\wrapper\dists' -Force; Remove-Item -LiteralPath $zip"
if errorlevel 1 exit /b 1
:run
call "%MAVEN_CMD%" %*
exit /b %errorlevel%
