@echo off
REM ─── Set JDK 21 for this project ─────────────────────────────────────────
REM Run: scripts\set-java21.bat
REM Then compile/run normally.

set JAVA_HOME=C:\tools\jdk21\jdk-21.0.5+11
set PATH=%JAVA_HOME%\bin;C:\tools\maven\apache-maven-3.9.9\bin;%PATH%

echo [OK] JAVA_HOME = %JAVA_HOME%
java -version
mvn --version
