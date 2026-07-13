@echo off
rem Build and run Braitenberg Vehicle 5.
rem
rem All .class files go into out\, never next to the sources.
rem
rem Do NOT use "java Vehicle5.java": that single-file launcher compiles only that one
rem file and loads the rest with a different class loader, which crashes with an
rem IllegalAccessError. The project is more than one file.

setlocal enabledelayedexpansion
cd /d "%~dp0"

rem cmd does not expand *.java for javac, so collect the file names ourselves.
set SOURCES=
for %%f in (*.java) do set SOURCES=!SOURCES! %%f

javac -d out !SOURCES!
if errorlevel 1 (
    echo.
    echo Compilation failed.
    pause
    exit /b 1
)

java -cp out Vehicle5
