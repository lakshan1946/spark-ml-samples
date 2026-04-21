@echo off
setlocal

cd /d "%~dp0"

echo Starting Lyrics 8-Class backend...
start "Lyrics 8-Class Backend" powershell -NoExit -ExecutionPolicy Bypass -File "%~dp0start-local.ps1"

echo Waiting for the server to become ready...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$url='http://localhost:9090/health'; for ($i = 0; $i -lt 180; $i++) { try { $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2; if ($resp.StatusCode -eq 200) { Start-Process 'http://localhost:9090'; exit 0 } } catch { Start-Sleep -Seconds 1 } }; exit 1"

if errorlevel 1 (
  echo The server did not become ready in time.
  echo Check the backend window for build or runtime errors.
)

endlocal