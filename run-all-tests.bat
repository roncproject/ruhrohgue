@echo off
setlocal enabledelayedexpansion
echo =======================================================================
echo 🚀 STARTING UNIFIED RUHROHGUE ACCEPTANCE TEST SUITE
echo =======================================================================
echo.

echo Checking that the app is reachable on http://localhost:8081/ ...
set APP_UP=0
for /l %%i in (1,1,10) do (
    for /f %%c in ('curl -s -o nul -w "%%{http_code}" http://localhost:8081/ 2^>nul') do set HTTP_CODE=%%c
    if "!HTTP_CODE!"=="200" (
        set APP_UP=1
        goto :app_is_up
    )
    ping -n 2 127.0.0.1 >nul
)
:app_is_up

if "!APP_UP!"=="0" (
    echo.
    echo ❌ No app responding on http://localhost:8081/ after 10 seconds.
    echo This script does not start the app for you — start it first, e.g.:
    echo    java -jar target\ruhrohgue.jar --server.port=8081
    echo.
    exit /b 1
)
echo App is up — proceeding.
echo.

echo [1/2] Running Java Playwright Page Object Model Tests...
cd tests\e2e
call mvn test
if %errorlevel% neq 0 (
    echo ❌ Playwright tests failed! Aborting suite.
    exit /b %errorlevel%
)
cd ..\..

echo.
echo [2/2] Running Postman API Flood Attack ^& Generating HTML Dashboard...
call newman run tests\api\RuhRohgue_Postman_Collection.json --env-var "baseUrl=http://localhost:8081" -n 50 --reporters cli

echo.
echo =======================================================================
echo 🎉 ALL CHECKPOINTS PASSED SUCCESSFULLY!
echo =======================================================================
