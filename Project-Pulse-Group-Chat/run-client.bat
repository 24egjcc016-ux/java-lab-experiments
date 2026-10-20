@echo off
setlocal
cd /d "%~dp0"

if not "%~1"=="" set "PULSE_NAME=%~1"

call mvn -q javafx:run
if errorlevel 1 pause
