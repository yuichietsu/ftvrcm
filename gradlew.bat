@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_SHARED_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper-shared.jar
set WRAPPER_CLI_JAR=%APP_HOME%gradle\wrapper\gradle-cli.jar
if not exist "%WRAPPER_JAR%" (
  echo Missing %WRAPPER_JAR%
  exit /b 1
)
if not exist "%WRAPPER_SHARED_JAR%" (
  echo Missing %WRAPPER_SHARED_JAR%
  exit /b 1
)
if not exist "%WRAPPER_CLI_JAR%" (
  echo Missing %WRAPPER_CLI_JAR%
  exit /b 1
)
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)
rem Android SDK: map ANDROID_SDK_ROOT to ANDROID_HOME if needed
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT (
  set ANDROID_HOME=%ANDROID_SDK_ROOT%
)
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%;%WRAPPER_SHARED_JAR%;%WRAPPER_CLI_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
