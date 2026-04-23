@ECHO OFF
SETLOCAL

SET "MVNW_PROJECTBASEDIR=%~dp0"

REM 仓库自带 Apache Maven（wrapper JAR 在部分环境下损坏时仍可构建）
IF EXIST "%MVNW_PROJECTBASEDIR%.mvn\apache-maven-3.9.9\bin\mvn.cmd" (
  PUSHD "%MVNW_PROJECTBASEDIR%"
  CALL "%MVNW_PROJECTBASEDIR%.mvn\apache-maven-3.9.9\bin\mvn.cmd" %*
  SET "ERR=%ERRORLEVEL%"
  POPD
  EXIT /B %ERR%
)

SET "WRAPPER_DIR=%MVNW_PROJECTBASEDIR%\.mvn\wrapper"
SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
SET "WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

IF NOT EXIST "%WRAPPER_PROPERTIES%" (
  ECHO [ERROR] Missing "%WRAPPER_PROPERTIES%".
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO [INFO] Downloading Maven Wrapper JAR...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 (
    ECHO [ERROR] Failed to download Maven Wrapper JAR.
    EXIT /B 1
  )
)

IF "%JAVA_HOME%"=="" (
  SET "JAVA_EXE=java"
) ELSE (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%MVNW_PROJECTBASEDIR%" -jar "%WRAPPER_JAR%" %*
EXIT /B %ERRORLEVEL%
