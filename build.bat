@echo off
REM ===================================================================
REM Stable Build Script for PatrolSpectatorPlugin
REM This script uses system-installed JDK and Maven from .maven folder
REM ===================================================================

setlocal

REM Find system JDK (Eclipse Adoptium)
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java not found at "%JAVA_HOME%"
    echo Please install Eclipse Adoptium JDK 17 or 21
    exit /b 1
)

REM Set Maven home to local installation
set "MAVEN_HOME=%~dp0.maven\apache-maven-3.9.6"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo ERROR: Maven not found at "%MAVEN_HOME%"
    exit /b 1
)

REM Set PATH
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

REM Change to project directory
cd /d "%~dp0"

echo ========================================
echo PatrolSpectatorPlugin Build Script
echo ========================================
echo.
echo JAVA_HOME: %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo Project Dir: %CD%
echo.

REM Test Java
echo Testing Java installation...
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 (
    echo ERROR: Java test failed
    exit /b 1
)
echo.

REM Test Maven
echo Testing Maven installation...
call "%MAVEN_HOME%\bin\mvn.cmd" --version
if errorlevel 1 (
    echo ERROR: Maven test failed
    exit /b 1
)
echo.

REM Run Maven build
echo ========================================
echo Starting Maven build...
echo ========================================
echo.
call "%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests

if errorlevel 1 (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESS
echo ========================================
echo.
echo Generated JAR files:
dir /b target\*.jar 2>nul
echo.

endlocal
