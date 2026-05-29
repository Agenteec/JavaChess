$vps_host = "agenteec-chess.ru"
$vps_user = "root"
$vps_project_path = "~/JavaChess/JavaChess"

Write-Host "=== JAVA CHESS DIRECT DEPLOYMENT SCRIPT ===" -ForegroundColor Green
Write-Host "1. Static files only (HTML/JS) - Instant, no Docker restart" -ForegroundColor Yellow
Write-Host "2. Full Backend Deploy - Upload Java + Rebuild Docker" -ForegroundColor Yellow
Write-Host "3. Exit" -ForegroundColor Gray

$choice = Read-Host "Select option (1-3)"

if ($choice -eq "1") {
    Write-Host ">>> Uploading HTML/JS files to VPS..." -ForegroundColor Cyan
    
    scp -r .\game-service\src\main\resources\static\* "${vps_user}@${vps_host}:${vps_project_path}/game-service/src/main/resources/static/"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[SUCCESS] Frontend updated! Refresh your browser using Ctrl+F5." -ForegroundColor Green
    } else {
        Write-Host "`n[ERROR] SCP file transfer failed." -ForegroundColor Red
    }
}
elseif ($choice -eq "2") {
    Write-Host ">>> Uploading backend Java source code..." -ForegroundColor Cyan
    
    scp -r .\game-service\src "${vps_user}@${vps_host}:${vps_project_path}/game-service/"
    scp .\game-service\pom.xml "${vps_user}@${vps_host}:${vps_project_path}/game-service/"
    
    scp -r .\user-service\src "${vps_user}@${vps_host}:${vps_project_path}/user-service/"
    scp .\user-service\pom.xml "${vps_user}@${vps_host}:${vps_project_path}/user-service/"
    
    scp .\docker-compose.yml "${vps_user}@${vps_host}:${vps_project_path}/"
    scp -r .\nginx "${vps_user}@${vps_host}:${vps_project_path}/"

    Write-Host ">>> Triggering Docker container rebuild on VPS over SSH..." -ForegroundColor Cyan
    
    ssh "${vps_user}@${vps_host}" "cd ${vps_project_path} && docker-compose up --build -d"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[SUCCESS] Backend deployed and Docker containers rebuilt successfully!" -ForegroundColor Green
    } else {
        Write-Host "`n[ERROR] Remote build failed." -ForegroundColor Red
    }
}
else {
    Write-Host "Exiting script." -ForegroundColor Gray
}