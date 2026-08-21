@echo off
setlocal

echo.
echo SACCOS LMS - k6 installer
echo.

where k6 >nul 2>nul
if not errorlevel 1 (
  echo k6 is already installed.
  k6 version
  echo.
  pause
  exit /b 0
)

where winget >nul 2>nul
if errorlevel 1 (
  echo Windows package manager was not found on this computer.
  echo Opening the official k6 install page instead.
  start "" "https://grafana.com/docs/k6/latest/set-up/install-k6/"
  echo.
  echo After installing k6, close and reopen this window, then run the memory test.
  pause
  exit /b 1
)

echo Installing k6 with winget. Windows may ask for permission.
winget install --id k6.k6 -e --accept-source-agreements --accept-package-agreements
if errorlevel 1 (
  echo.
  echo k6 installation did not finish successfully.
  echo Opening the official k6 install page as a fallback.
  start "" "https://grafana.com/docs/k6/latest/set-up/install-k6/"
  pause
  exit /b 1
)

echo.
echo k6 installation finished.
echo Close this window before running 03-run-memory-test.bat so Windows refreshes PATH.
pause
