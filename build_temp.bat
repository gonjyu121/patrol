@echo off
REM Set JAVA_HOME to system-installed JDK
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"

REM Set PATH with system JDK first to override broken local JDK
set "MAVEN_HOME=c:\Users\gonjy\Projects\Private\PatrolSpectatorPlugin\.maven\apache-maven-3.9.6"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

cd /d "c:\Users\gonjy\Projects\Private\PatrolSpectatorPlugin"

echo ===== Environment Check =====
echo JAVA_HOME: %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo.
echo Testing Java...
"%JAVA_HOME%\bin\java.exe" -version
echo.
echo Testing Maven...
call "%MAVEN_HOME%\bin\mvn.cmd" --version
echo.
echo ===== Starting Build =====
call "%MAVEN_HOME%\bin\mvn.cmd" clean package -q -DskipTests
