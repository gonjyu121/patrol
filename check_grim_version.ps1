$slug = "grimac"
$apiUrl = "https://api.modrinth.com/v2/project/$slug/version"
$versions = Invoke-RestMethod -Uri $apiUrl -Method Get

Write-Host "Top 5 GrimAC Versions on Modrinth:"
$versions | Select-Object -First 5 | ForEach-Object {
    Write-Host "Version: $($_.version_number)"
    Write-Host "Type:    $($_.version_type)"
    Write-Host "Date:    $($_.date_published)"
    Write-Host "-------------------"
}
