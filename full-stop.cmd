@echo off
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0full-stop.ps1" < nul
exit /b %ERRORLEVEL%
