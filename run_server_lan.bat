@echo off
echo ===================================================
echo   Distributed Quiz Server - LAN Launcher
echo ===================================================
echo.
echo Please ensure 'peers.txt' is configured with all server IPs.
echo.
set /p id="Enter this Server's Node ID (1-4): "

echo.
echo Starting Server Node %id%...
echo (It will bind to the IP specified in peers.txt line %id%)
echo.

java -cp "lib/*;bin" server.QuizServer %id%
pause
