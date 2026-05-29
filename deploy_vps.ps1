$vps_host = "agenteec-chess.ru"
$vps_user = "root"
$vps_project_path = "~/JavaChess/JavaChess"

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "=== AGENTEEC CHESS DIRECT DEPLOYMENT SCRIPT ===" -ForegroundColor Green
Write-Host "1. Static files only (React SPA) - Instant, no Docker restart" -ForegroundColor Yellow
Write-Host "2. Full Backend Deploy - Upload Java + Rebuild Docker" -ForegroundColor Yellow
Write-Host "3. Exit" -ForegroundColor Gray

$choice = Read-Host "Select option (1-3)"

if ($choice -eq "1" -or $choice -eq "2") {
    Write-Host "`n>>> Compiling React Frontend locally..." -ForegroundColor Cyan

    Set-Location .\frontend
    npm run build
    Set-Location ..

    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nReact compilation failed! Aborting deploy." -ForegroundColor Red
        Exit
    }
    Write-Host "✔ React compiled successfully!" -ForegroundColor Green
}

if ($choice -eq "1") {
    Write-Host "`n>>> Uploading compiled React static files to VPS..." -ForegroundColor Cyan

    scp -r .\game-service\src\main\resources\static\* "${vps_user}@${vps_host}:${vps_project_path}/game-service/src/main/resources/static/"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[SUCCESS] Agenteec Chess Frontend updated! Refresh your browser (Ctrl+F5)." -ForegroundColor Green
    } else {
        Write-Host "`n[ERROR] SCP file transfer failed." -ForegroundColor Red
    }
}
elseif ($choice -eq "2") {
    Write-Host "`n>>> Uploading backend Java source code (with built static files)..." -ForegroundColor Cyan

    scp -r .\game-service\src "${vps_user}@${vps_host}:${vps_project_path}/game-service/"
    scp .\game-service\pom.xml "${vps_user}@${vps_host}:${vps_project_path}/game-service/"

    scp -r .\user-service\src "${vps_user}@${vps_host}:${vps_project_path}/user-service/"
    scp .\user-service\pom.xml "${vps_user}@${vps_host}:${vps_project_path}/user-service/"

    scp .\docker-compose.yml "${vps_user}@${vps_host}:${vps_project_path}/"
    scp -r .\nginx "${vps_user}@${vps_host}:${vps_project_path}/"

    Write-Host "`n>>> Triggering Docker container rebuild on VPS over SSH..." -ForegroundColor Cyan

    ssh "${vps_user}@${vps_host}" "cd ${vps_project_path} && docker compose up --build -d"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[SUCCESS] Agenteec Chess Backend and Frontend deployed successfully!" -ForegroundColor Green
    } else {
        Write-Host "`n[ERROR] Remote build failed." -ForegroundColor Red
    }
}
else {
    Write-Host "Exiting script." -ForegroundColor Gray
}