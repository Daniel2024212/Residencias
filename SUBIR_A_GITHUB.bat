@echo off
cd /d "%~dp0"
git init
git branch -M main
git add .
git commit -m "Subir proyecto Android EvoGMD con clasificacion de probabilidad"
git remote remove origin 2>nul
git remote add origin https://github.com/Daniel2024212/Residencias.git
git push -u origin main
pause
