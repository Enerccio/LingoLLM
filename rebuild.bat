@echo off
setlocal

rem Usage: rebuild.bat [--force]
rem --force rebuilds and restarts even when there are no new commits.
set FORCE=0
if "%~1"=="--force" (
    set FORCE=1
) else if not "%~1"=="" (
    echo Usage: %~nx0 [--force]
    exit /b 1
)

rem Windows version of rebuild.sh: updates the checkout, rebuilds the image, restarts the container.
rem Run it from the repository root.

rem Fetch the latest commit info from the remote repository
git fetch origin || goto :fail_fetch

rem Compare current local commit with remote upstream commit
for /f %%i in ('git rev-parse HEAD') do set LOCAL=%%i
for /f %%i in ('git rev-parse @{u}') do set REMOTE=%%i

if "%LOCAL%"=="%REMOTE%" if "%FORCE%"=="0" (
    echo No updates found in Git repository. Containers remain running.
    exit /b 0
)

if "%FORCE%"=="1" (
    echo Forced rebuild. Updating services...
) else (
    echo New changes detected. Updating services...
)

rem Bring down existing containers
docker compose down

rem Pull the new code
git pull || goto :fail_pull

rem Rebuild and start containers in detached mode
docker compose up -d --build
exit /b %errorlevel%

:fail_fetch
echo Git fetch failed
exit /b 1

:fail_pull
echo Git pull failed
exit /b 1

