@echo off
cd /d "%~dp0"
git add -A
git diff --cached --quiet || git commit -m "auto-backup: %date% %time%"
