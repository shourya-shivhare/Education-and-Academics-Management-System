@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script (Windows)
@REM Auto-downloads Maven 3.9.6 on first run.
@REM ----------------------------------------------------------------------------
@echo off
set MAVEN_PROJECTBASEDIR=%~dp0
set MVNW_REPOURL=https://repo.maven.apache.org/maven2

if not exist "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" (
    powershell -Command "Invoke-WebRequest -Uri '%MVNW_REPOURL%/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar'"
)

java -jar "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" %*
