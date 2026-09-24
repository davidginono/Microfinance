@echo off
setlocal

echo.
echo SACCOS LMS - k6 installer
echo.

set "K6_EXE="
call :find_k6
if defined K6_EXE (
  echo k6 is already installed at:
  echo %K6_EXE%
  "%K6_EXE%" version
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
set "K6_EXE="
call :find_k6
if defined K6_EXE (
  echo k6 was found at:
  echo %K6_EXE%
  "%K6_EXE%" version
  echo.
  echo You can run 03-run-memory-test.bat now.
) else (
  echo Close this window before running 03-run-memory-test.bat so Windows refreshes PATH.
)
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
