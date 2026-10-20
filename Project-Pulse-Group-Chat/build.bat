@echo off
setlocal
cd /d "%~dp0"

echo Building Pulse Chat...
echo.

call mvn -q clean package
if errorlevel 1 (
    echo.
    echo Build failed. Check the output above.
    pause
    exit /b 1
)

echo.
echo Build OK. Start the server with run-server.bat, then open one or more
echo clients with run-client.bat.
pause
