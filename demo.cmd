@echo off
REM One-command local interview demo. See docs\DEMO.md.
REM   demo.cmd          start (first run creates .env.demo)
REM   demo.cmd -Down    stop      demo.cmd -Wipe    stop and delete demo data
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo-up.ps1" %*
