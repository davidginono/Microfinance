@echo off
setlocal

cd /d "%~dp0..\.."

echo.
echo SACCOS LMS - start app for memory testing
echo.
echo This window will stay open while the app is running.
echo Wait until you see "Started SaccoLoanApplication", then run 03-run-memory-test.bat.
echo.

if not exist "load-tests\results" mkdir "load-tests\results"

set "SPRING_PROFILES_ACTIVE=benchmark"
set "SERVER_PORT=8080"
set "JAVA_TOOL_OPTIONS=-Xms512m -Xmx512m -XX:NativeMemoryTracking=summary -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=load-tests/results -XX:StartFlightRecording=filename=load-tests/results/saccos-beginner-memory.jfr,settings=profile,dumponexit=true -Xlog:gc*:file=load-tests/results/gc.log:time,uptime,level,tags"

echo Building the application first...
call mvn -DskipTests package
if errorlevel 1 (
  echo.
  echo Build failed. Fix the build error above, then run this file again.
  pause
  exit /b 1
)

echo.
echo Starting on http://localhost:8080 with the benchmark profile...
java -jar target\ROOT.war

echo.
echo The app stopped.
pause
