@echo off
:: 1. Force the session to use JDK 17
set "JAVA_HOME=c:\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: 2. Navigate to project root
cd /d "C:\Users\thoma\eclipse-workspace\stave-reader"

:: 3. Clean and Build the project
echo Building Milestone Version...
call mvn clean install -DskipTests

:: 4. Launch only if the build succeeded
if %ERRORLEVEL% EQU 0 (
    echo Build Successful! Launching Stave Reader...
    "c:\jdk-17\bin\java.exe" -jar target\stave-reader-1.0.0.jar "C:\temp\pdfs\BWV 915.pdf"
) else (
    echo.
    echo BUILD FAILED! Check the Maven errors above.
    pause
)