@echo off
REM Builds a native Windows installer for FreeLabels (.exe by default, or .msi).
REM
REM Requirements on the build machine (must be run ON Windows - jpackage
REM cannot cross-compile installers for a different OS):
REM   - JDK 17+ (jpackage ships with the JDK since Java 14)
REM   - WiX Toolset v3.x installed and on PATH (https://wixtoolset.org/)
REM     jpackage uses WiX under the hood for both --type msi and --type exe.
REM
REM Usage (from a Developer Command Prompt, in the freelabels-swing folder):
REM   mvn package
REM   packaging\windows\build-installer.bat [exe|msi]

setlocal
cd /d "%~dp0..\.."

set TYPE=%1
if "%TYPE%"=="" set TYPE=exe

set APP_NAME=FreeLabels
set APP_VERSION=1.0.0
set VENDOR=MindNotion
set MAIN_JAR=freelabels.jar
set MAIN_CLASS=com.freelabels.LabelApp
set ICON=packaging\icons\icon.ico
set JAR_PATH=target\%MAIN_JAR%
set INPUT_DIR=target\jpackage-input
set OUT_DIR=target\installer

if not exist "%JAR_PATH%" (
  echo Error: %JAR_PATH% not found. Run "mvn package" first.
  exit /b 1
)

if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%INPUT_DIR%"
mkdir "%OUT_DIR%"
copy "%JAR_PATH%" "%INPUT_DIR%\" >nul

jpackage ^
  --type %TYPE% ^
  --input "%INPUT_DIR%" ^
  --dest "%OUT_DIR%" ^
  --name "%APP_NAME%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --description "FreeLabels - Free Label Creation Platform" ^
  --icon "%ICON%" ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser

echo.
echo Built installer in %OUT_DIR%
dir "%OUT_DIR%"
endlocal
