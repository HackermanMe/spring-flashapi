@REM ----------------------------------------------------------------------------
@REM Maven Wrapper script for Windows
@REM Downloads and caches Apache Maven if not already present.
@REM ----------------------------------------------------------------------------
@echo off
@setlocal enableextensions enabledelayedexpansion

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

set PROPERTIES_FILE=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties
set MAVEN_USER_HOME=%USERPROFILE%\.m2
set WRAPPER_CACHE_DIR=%MAVEN_USER_HOME%\wrapper\dists

@REM Read distributionUrl from properties
if not exist "%PROPERTIES_FILE%" (
    echo ERROR: Could not find %PROPERTIES_FILE%
    goto error
)

for /f "usebackq tokens=1,* delims==" %%a in ("%PROPERTIES_FILE%") do (
    if "%%a"=="distributionUrl" set DISTRIBUTION_URL=%%b
)

if "%DISTRIBUTION_URL%"=="" (
    echo ERROR: distributionUrl not found in %PROPERTIES_FILE%
    goto error
)

@REM Derive Maven version and cache path
for %%i in ("%DISTRIBUTION_URL%") do set MAVEN_ZIP_NAME=%%~nxi
set MAVEN_DIR_NAME=%MAVEN_ZIP_NAME:-bin.zip=%
set MAVEN_HOME_DIR=%WRAPPER_CACHE_DIR%\%MAVEN_DIR_NAME%

@REM Download and extract if not cached
if not exist "%MAVEN_HOME_DIR%\bin\mvn.cmd" (
    echo Downloading Maven from %DISTRIBUTION_URL%...
    if not exist "%WRAPPER_CACHE_DIR%" mkdir "%WRAPPER_CACHE_DIR%"

    set TMP_FILE=%WRAPPER_CACHE_DIR%\%MAVEN_ZIP_NAME%

    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%DISTRIBUTION_URL%', '!TMP_FILE!') }"

    if not exist "!TMP_FILE!" (
        echo ERROR: Failed to download Maven.
        goto error
    )

    echo Extracting Maven...
    powershell -Command "& { Expand-Archive -Path '!TMP_FILE!' -DestinationPath '%WRAPPER_CACHE_DIR%' -Force }"
    del /f "!TMP_FILE!"

    if not exist "%MAVEN_HOME_DIR%\bin\mvn.cmd" (
        echo ERROR: Maven extraction failed.
        goto error
    )
)

@REM Find Java
if not "%JAVA_HOME%"=="" (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    where java >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set JAVACMD=java
    ) else (
        echo ERROR: JAVA_HOME is not set and 'java' is not in PATH.
        goto error
    )
)

@REM Run Maven
"%MAVEN_HOME_DIR%\bin\mvn.cmd" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %MAVEN_OPTS% %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
cmd /C exit /B %ERROR_CODE%
