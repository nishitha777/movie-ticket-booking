@echo off
cd /d "%~dp0"

if not exist out mkdir out

javac -cp "lib/*" -d out src\main\java\local\basilbyte\MovieTicketServer.java
if errorlevel 1 exit /b 1

java -cp "out;lib/*" local.basilbyte.MovieTicketServer
