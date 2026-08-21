@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto run

echo Bootstrapping Gradle wrapper 9.5.1...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-9.5.1-wrapper.jar'; $d='%WRAPPER_JAR%'; New-Item -ItemType Directory -Force -Path (Split-Path $d) ^| Out-Null; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $d; $h=(Get-FileHash $d -Algorithm SHA256).Hash.ToLower(); if($h -ne '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7'){Remove-Item $d -Force; throw 'Gradle wrapper checksum mismatch'}"
if errorlevel 1 exit /b 1

:run
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -Xmx128m -Xms64m -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
