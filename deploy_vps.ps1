$vps_host = 'agenteec-chess.ru'
$vps_user = 'root'
$vps_project_path = '~/JavaChess/JavaChess'

Write-Host '=== AGENTEEC CHESS DIRECT DEPLOYMENT SCRIPT ===' -ForegroundColor Green
Write-Host '1. Static files only (React SPA) - Instant, no Docker restart' -ForegroundColor Yellow
Write-Host '2. Full Backend Deploy - Upload Java + Rebuild Docker' -ForegroundColor Yellow
Write-Host '3. Exit' -ForegroundColor Gray

$choice = Read-Host 'Select option (1-3)'

if (($choice -eq '1') -or ($choice -eq '2')) {
    Write-Host '>>> Compiling React Frontend locally...' -ForegroundColor Cyan

    Set-Location .\frontend
    npm run build
    Set-Location ..

    if ($LASTEXITCODE -ne 0) {
        Write-Host 'React compilation failed! Aborting deploy.' -ForegroundColor Red
        Exit
    }
    Write-Host 'React compiled successfully!' -ForegroundColor Green
}

if ($choice -eq '1') {
    Write-Host '>>> Uploading compiled React static files to VPS...' -ForegroundColor Cyan

    $dest = $vps_user + '@' + $vps_host + ':' + $vps_project_path + '/game-service/src/main/resources/static/'
    scp -r .\game-service\src\main\resources\static\* $dest

    if ($LASTEXITCODE -eq 0) {
        Write-Host 'Agenteec Chess Frontend updated! Refresh your browser (Ctrl + F5).' -ForegroundColor Green
    } else {
        Write-Host 'SCP file transfer failed.' -ForegroundColor Red
    }
}
elseif ($choice -eq '2') {
    Write-Host '>>> Uploading backend Java source code...' -ForegroundColor Cyan

    $dest_game_src = $vps_user + '@' + $vps_host + ':' + $vps_project_path + '/game-service/'
    scp -r .\game-service\src $dest_game_src
    scp .\game-service\pom.xml $dest_game_src

    $dest_user_src = $vps_user + '@' + $vps_host + ':' + $vps_project_path + '/user-service/'
    scp -r .\user-service\src $dest_user_src
    scp .\user-service\pom.xml $dest_user_src

    $dest_root = $vps_user + '@' + $vps_host + ':' + $vps_project_path + '/'
    scp .\docker-compose.yml $dest_root
    scp -r .\nginx $dest_root

    Write-Host '>>> Triggering Docker container rebuild on VPS over SSH...' -ForegroundColor Cyan

    $ssh_target = $vps_user + '@' + $vps_host
    $ssh_cmd = 'cd ' + $vps_project_path + ' && docker compose up --build -d'
    ssh $ssh_target $ssh_cmd

    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Remote build failed.' -ForegroundColor Red
    } else {
        Write-Host 'Agenteec Chess Backend and Frontend deployed successfully!' -ForegroundColor Green
    }
}
else {
    Write-Host 'Exiting script.' -ForegroundColor Gray
}