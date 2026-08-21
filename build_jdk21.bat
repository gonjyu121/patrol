@echo off
REM ===================================================================
REM Build Script with Portable JDK 21
REM ===================================================================

setlocal

REM Set JAVA_HOME to the portable JDK
set "JAVA_HOME=%~dp0.maven\jdk21\jdk-21.0.9+10"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Portable Java not found at "%JAVA_HOME%"
    exit /b 1
)

REM Set Maven home (existing)
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
echo PatrolSpectatorPlugin Build (JDK 21)
echo ========================================
echo.
echo JAVA_HOME: %JAVA_HOME%
echo.

REM Test Java
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 exit /b 1
echo.

REM Run Maven build
echo Starting Maven build...
call "%MAVEN_HOME%\bin\mvn.cmd" clean package

if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)

echo.
echo BUILD SUCCESS
echo Generated JAR files:
dir /b target\*.jar 2>nul
echo.

REM ====================================================
REM ビルドしたJARをpluginsフォルダにコピー
REM ====================================================
if not exist "%~dp0plugins" mkdir "%~dp0plugins"
for %%f in (target\PatrolSpectatorPlugin-*.jar) do (
    echo Copying %%f to plugins\...
    copy /Y "%%f" "%~dp0plugins\" >nul
    echo Deployed: plugins\%%~nxf
)

endlocal

