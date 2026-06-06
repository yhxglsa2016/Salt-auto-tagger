@echo off
setlocal
set "JAVA_HOME=D:\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 exit /b 1

call "%~dp0gradlew.bat" plugin
exit /b %errorlevel%
