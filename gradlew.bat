
@ECHO OFF
SET DIR=%~dp0
SET WRAPPER_JAR=%DIR%\gradle\wrapper\gradle-wrapper.jar
java -jar "%WRAPPER_JAR%" %*
