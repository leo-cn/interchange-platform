@echo off
setlocal

rem ==========================================================================
rem  Interchange Platform - one-click start
rem
rem  Database is decided by Profile, default is mysql:
rem      schema interchange (auto created), user root / pass 123
rem  To use another database: change DB_TYPE below to oracle / postgresql / h2
rem
rem  To override connection params without editing config files, set env vars:
rem      set "DB_URL=jdbc:mysql://192.168.1.10:3306/interchange?useSSL=false"
rem      set "DB_USER=root"
rem      set "DB_PASSWORD=yourpassword"
rem  DO NOT pass these as EMPTY command line args (e.g. --spring.datasource.url=),
rem  an empty value wipes the config; the built-in startup check will block it.
rem
rem  NOTE: keep this file ASCII-only + CRLF line endings.
rem  Chinese text breaks cmd.exe parsing under some code pages.
rem ==========================================================================

set "DB_TYPE=mysql"

rem ---- fallback: empty DB_TYPE goes back to mysql, never pass an empty arg ----
if "%DB_TYPE%"=="" set "DB_TYPE=mysql"

rem ---- port: default 18080 ----
rem The dynamic port range on this machine is 1024-15000, so 8080 falls inside
rem it and can be randomly borrowed by any process (e.g. Windows Defender),
rem which makes Tomcat fail with "Port 8080 was already in use".
rem If your dynamic range is the default 49152-65535, you may switch to 8080.
if "%APP_PORT%"=="" set "APP_PORT=18080"

set "JAR=%~dp0target\interchange-platform.jar"

if not exist "%JAR%" (
    echo [ERROR] jar not found: %JAR%
    echo         Build it first:
    echo         D:\Java\maven\apache-maven-3.9.2\bin\mvn.cmd -DskipTests clean package
    pause
    exit /b 1
)

echo ==========================================================
echo   Interchange Platform
echo   Profile : %DB_TYPE%
echo   Port    : %APP_PORT%
echo   Login   : http://127.0.0.1:%APP_PORT%/login
echo   Account : admin / Admin@123
echo ==========================================================
echo.
echo   Keep this window OPEN. Closing it stops the service.
echo   Press Ctrl+C to stop.
echo.

java -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=%DB_TYPE% --server.port=%APP_PORT%

echo.
echo [Interchange Platform] Service stopped. Press any key to close.
pause >nul

endlocal
