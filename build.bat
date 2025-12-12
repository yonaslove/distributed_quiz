@echo off
if not exist "bin" mkdir bin
echo Compiling source code...
javac -cp "lib/*" -d bin src/common/*.java src/server/*.java src/client/*.java
if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b %errorlevel%
)
echo Compilation Success!
pause
