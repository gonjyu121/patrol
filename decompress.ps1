$inputPath = 'C:\Users\gonjy\Downloads\2026-01-22-2.log.gz'
$outputPath = 'C:\Users\gonjy\Downloads\2026-01-22-2.log'
try {
    $input = [System.IO.File]::OpenRead($inputPath)
    $output = [System.IO.File]::Create($outputPath)
    $gzip = New-Object System.IO.Compression.GZipStream $input, [System.IO.Compression.CompressionMode]::Decompress
    $gzip.CopyTo($output)
    $gzip.Dispose()
    $output.Dispose()
    $input.Dispose()
    Write-Host "Success"
}
catch {
    Write-Error $_.Exception.Message
}
