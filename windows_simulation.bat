@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\..\scripts\windows_repository.ps1" -Repository "%~dp0." -Logs %*
exit /b %ERRORLEVEL%
