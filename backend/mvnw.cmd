@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch script for Windows
@REM Downloads Apache Maven 3.9.6 on first run, then delegates to it.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP=%MAVEN_HOME%-bin.zip
set MAVEN_BIN=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd
set MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "%MAVEN_BIN%" (
    echo [mvnw] Maven %MAVEN_VERSION% not found. Downloading...
    if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%' -UseBasicParsing"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%MAVEN_HOME%' -Force"
    echo [mvnw] Maven downloaded successfully.
)

"%MAVEN_BIN%" %*
