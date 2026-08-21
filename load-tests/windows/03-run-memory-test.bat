@echo off
setlocal

cd /d "%~dp0..\.."

echo.
echo SACCOS LMS - beginner k6 memory test
echo.

set "K6_EXE="
call :find_k6
if not defined K6_EXE (
  echo k6 was not found.
  echo Double-click load-tests\windows\01-install-k6.bat first.
  echo Then close and reopen this window.
  pause
  exit /b 1
)

if not exist "load-tests\results" mkdir "load-tests\results"

set "BASE_URL=http://localhost:8080"
set /p "BASE_URL_INPUT=App URL [http://localhost:8080]: "
if not "%BASE_URL_INPUT%"=="" set "BASE_URL=%BASE_URL_INPUT%"

set "SACCOS_ACCOUNT_TYPE=staff"
set /p "ACCOUNT_TYPE_INPUT=Account type: staff or member [staff]: "
if not "%ACCOUNT_TYPE_INPUT%"=="" set "SACCOS_ACCOUNT_TYPE=%ACCOUNT_TYPE_INPUT%"

set "SACCOS_USERNAME=ADM001"
set /p "USERNAME_INPUT=Username or staff number [ADM001]: "
if not "%USERNAME_INPUT%"=="" set "SACCOS_USERNAME=%USERNAME_INPUT%"

echo.
echo The password will be visible while typing because this is plain Command Prompt.
set /p "SACCOS_PASSWORD=Password: "
if "%SACCOS_PASSWORD%"=="" (
  echo Password is required.
  pause
  exit /b 1
)

set "SACCOS_ROUTES=/admin/dashboard,/admin/users,/admin/settings-controls"
if /I "%SACCOS_ACCOUNT_TYPE%"=="member" set "SACCOS_ROUTES=/app/dashboard,/app/loan-products,/app/loan-applications,/app/guarantee-requests"

echo.
echo Default routes: %SACCOS_ROUTES%
set /p "ROUTES_INPUT=Routes to test, comma separated [press Enter to keep default]: "
if not "%ROUTES_INPUT%"=="" set "SACCOS_ROUTES=%ROUTES_INPUT%"

set "SACCOS_TARGET_RPS=25"
set /p "RPS_INPUT=Page requests per second [25]: "
if not "%RPS_INPUT%"=="" set "SACCOS_TARGET_RPS=%RPS_INPUT%"

set "SACCOS_SOAK_DURATION=10m"
set /p "DURATION_INPUT=How long to hold that load, for example 10m or 30m [10m]: "
if not "%DURATION_INPUT%"=="" set "SACCOS_SOAK_DURATION=%DURATION_INPUT%"

set "SACCOS_MAX_VUS=150"
set /p "VUS_INPUT=Maximum virtual users [150]: "
if not "%VUS_INPUT%"=="" set "SACCOS_MAX_VUS=%VUS_INPUT%"

echo.
echo Running k6 now...
echo Using k6:
echo %K6_EXE%
echo Results will be saved in load-tests\results.
echo.

"%K6_EXE%" run ^
  --summary-export "load-tests/results/beginner-memory-summary.json" ^
  --out "csv=load-tests/results/beginner-memory-timeseries.csv" ^
  "load-tests/k6/saccos-memory-beginner.js"

echo.
echo Finished.
echo Main report: load-tests\results\beginner-memory-report.txt
echo Timeline CSV: load-tests\results\beginner-memory-timeseries.csv
echo.
pause

exit /b 0

:find_k6
where k6 >nul 2>nul
if not errorlevel 1 (
  for /f "delims=" %%K in ('where k6 2^>nul') do (
    if not defined K6_EXE set "K6_EXE=%%K"
  )
)
if not defined K6_EXE if exist "%ProgramFiles%\k6\k6.exe" set "K6_EXE=%ProgramFiles%\k6\k6.exe"
if not defined K6_EXE if exist "%ProgramFiles%\Grafana Labs\k6\k6.exe" set "K6_EXE=%ProgramFiles%\Grafana Labs\k6\k6.exe"
if not defined K6_EXE if exist "%LOCALAPPDATA%\Microsoft\WindowsApps\k6.exe" set "K6_EXE=%LOCALAPPDATA%\Microsoft\WindowsApps\k6.exe"
exit /b 0
