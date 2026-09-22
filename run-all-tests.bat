@echo off
echo =======================================================================
echo 🚀 STARTING UNIFIED RUHROHGUE ACCEPTANCE TEST SUITE
echo =======================================================================
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
echo [2/2] Running Postman API Flood Attack & Generating HTML Dashboard...
mkdir tests\api\reports 2>nul
call newman run tests\api\RuhRohgue_Postman_Collection.json --env-var "baseUrl=http://localhost:8081" -n 50 --reporters cli,htmlextra --reporter-htmlextra-export tests\api\reports\api_security_report.html

echo.
echo =======================================================================
echo 🎉 ALL CHECKPOINTS PASSED SUCCESSFULLY!
echo 📊 Open your browser to view the API archive report:
echo    tests\api\reports\api_security_report.html
echo =======================================================================
