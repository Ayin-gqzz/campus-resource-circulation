@echo off
setlocal
cd /d "%~dp0"
set "DEMO_JAR=%~dp0target\campus-resource-circulation-buy-sell.jar"
if not exist "%DEMO_JAR%" set "DEMO_JAR=%~dp0target\campus-resource-circulation-0.1.0-SNAPSHOT.jar"
set "DEMO_JAVA="
if not exist "%DEMO_JAR%" (
    echo Demo program not found: %DEMO_JAR%
    echo Please run mvn clean verify in this folder first.
    pause
    exit /b 1
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "DEMO_JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined DEMO_JAVA if exist "%USERPROFILE%\.jdks\ms-21.0.11\bin\javaw.exe" set "DEMO_JAVA=%USERPROFILE%\.jdks\ms-21.0.11\bin\javaw.exe"
if not defined DEMO_JAVA for /f "delims=" %%J in ('where javaw.exe 2^>nul') do if not defined DEMO_JAVA set "DEMO_JAVA=%%J"
if not defined DEMO_JAVA (
    echo Java 17 or newer is required. Please set JAVA_HOME to your JDK folder.
    pause
    exit /b 1
)
start "" "%DEMO_JAVA%" "-Dcampus.demo.db=%~dp0data\demo.db" -jar "%DEMO_JAR%"
exit /b
