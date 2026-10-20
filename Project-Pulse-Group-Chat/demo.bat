@echo off
setlocal
cd /d "%~dp0"

echo Starting one server and three clients.
echo Give each window a few seconds to open. Close the server window to stop.
echo.

start "Pulse Server" "%~dp0run-server.bat"
timeout /t 7 /nobreak >nul

start "Alice" "%~dp0run-client.bat" alice
timeout /t 2 /nobreak >nul

start "Bob" "%~dp0run-client.bat" bob
timeout /t 2 /nobreak >nul

start "Carol" "%~dp0run-client.bat" carol

exit /b 0
