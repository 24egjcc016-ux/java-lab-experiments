@echo off
setlocal
cd /d "%~dp0"

if not "%~1"=="" set "PULSE_PORT=%~1"

call mvn -q javafx:run -Pserver
if errorlevel 1 pause
