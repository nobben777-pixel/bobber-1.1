@echo off
setlocal
where java >nul 2>nul || (echo Java is not in PATH. Install Java 21 first.& exit /b 1)
where gradle >nul 2>nul || (echo Gradle is not in PATH. Install Gradle or open this folder in IntelliJ IDEA as a Gradle project.& exit /b 1)
gradle build
if errorlevel 1 exit /b %errorlevel%
echo.
echo Build finished. See build\libs\
