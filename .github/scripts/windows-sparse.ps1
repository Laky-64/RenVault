param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$Version
)

$ErrorActionPreference = 'Stop'

if (-not $env:WINDOWS_CERT_PFX -or -not $env:WINDOWS_CERT_PASSWORD) {
    Write-Host 'No signing certificate configured, skipping the Windows passkey package'
    exit 0
}

$sdk = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin' -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'x64\makeappx.exe') } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (-not $sdk) { throw 'Windows SDK tools not found' }
$tools = Join-Path $sdk.FullName 'x64'
$makeappx = Join-Path $tools 'makeappx.exe'
$signtool = Join-Path $tools 'signtool.exe'
$mt = Join-Path $tools 'mt.exe'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $root 'desktopApp\windows'
$work = Join-Path $env:RUNNER_TEMP 'renvault-sparse'
$stage = Join-Path $work 'package'
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'Assets') | Out-Null

$parts = ($Version -split '[^0-9]+' | Where-Object { $_ -ne '' } | Select-Object -First 3)
while ($parts.Count -lt 3) { $parts += '0' }
$packageVersion = ($parts -join '.') + '.0'
(Get-Content (Join-Path $source 'AppxManifest.xml') -Raw).Replace('__VERSION__', $packageVersion) |
    Set-Content -Path (Join-Path $stage 'AppxManifest.xml') -Encoding utf8

Add-Type -AssemblyName System.Drawing
$icon = [System.Drawing.Image]::FromFile((Join-Path $root 'desktopApp\icons\renvault.png'))
foreach ($asset in @(@('StoreLogo.png', 50), @('Square44x44Logo.png', 44), @('Square150x150Logo.png', 150))) {
    $bitmap = New-Object System.Drawing.Bitmap $asset[1], $asset[1]
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($icon, 0, 0, $asset[1], $asset[1])
    $bitmap.Save((Join-Path $stage "Assets\$($asset[0])"), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}
$icon.Dispose()

$exe = Join-Path $AppImage 'RenVault.exe'
Set-ItemProperty -Path $exe -Name IsReadOnly -Value $false
& $mt -nologo -inputresource:"$exe;#1" -manifest (Join-Path $source 'RenVault.exe.manifest') -outputresource:"$exe;#1"
if ($LASTEXITCODE -ne 0) { throw "mt.exe failed with $LASTEXITCODE" }

$pfx = Join-Path $work 'signing.pfx'
[IO.File]::WriteAllBytes($pfx, [Convert]::FromBase64String($env:WINDOWS_CERT_PFX))
try {
    $msix = Join-Path $AppImage 'RenVault.msix'
    & $makeappx pack /o /nv /d $stage /p $msix
    if ($LASTEXITCODE -ne 0) { throw "makeappx failed with $LASTEXITCODE" }
    & $signtool sign /q /fd SHA256 /f $pfx /p $env:WINDOWS_CERT_PASSWORD $msix
    if ($LASTEXITCODE -ne 0) { throw "signtool failed with $LASTEXITCODE" }

    $certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($pfx, $env:WINDOWS_CERT_PASSWORD)
    [IO.File]::WriteAllBytes((Join-Path $AppImage 'RenVault.cer'), $certificate.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
} finally {
    Remove-Item $pfx -Force -ErrorAction SilentlyContinue
}
