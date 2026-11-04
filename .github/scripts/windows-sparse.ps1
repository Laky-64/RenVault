param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$Version
)

$ErrorActionPreference = 'Stop'

if (-not $env:WINDOWS_CERT_PFX -or -not $env:WINDOWS_CERT_PASSWORD) {
    if ($env:GITHUB_REF_TYPE -eq 'tag') { throw 'No signing certificate configured, releases need the Windows passkey package' }
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
$properties = @{}
Get-Content (Join-Path $root 'gradle.properties') | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $properties[$Matches[1]] = $Matches[2] }
}
$appId = $properties['renvault.id']
$appName = $properties['renvault.name']
if (-not $appId -or -not $appName) { throw 'renvault.id and renvault.name must be set in gradle.properties' }
$source = Join-Path $root 'desktopApp\windows'
$work = Join-Path $env:RUNNER_TEMP 'renvault-sparse'
$stage = Join-Path $work 'package'
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'Assets') | Out-Null

$parts = ($Version -split '[^0-9]+' | Where-Object { $_ -ne '' } | Select-Object -First 3)
while ($parts.Count -lt 3) { $parts += '0' }
$packageVersion = ($parts -join '.') + '.0'
(Get-Content (Join-Path $source 'AppxManifest.xml') -Raw).Replace('__VERSION__', $packageVersion).Replace('__ID__', $appId).Replace('__NAME__', $appName) |
    Set-Content -Path (Join-Path $stage 'AppxManifest.xml') -Encoding utf8
$exeManifest = Join-Path $work 'app.exe.manifest'
(Get-Content (Join-Path $source 'RenVault.exe.manifest') -Raw).Replace('__ID__', $appId).Replace('__NAME__', $appName) |
    Set-Content -Path $exeManifest -Encoding utf8

Add-Type -AssemblyName System.Drawing
$icon = [System.Drawing.Image]::FromFile((Join-Path $root 'desktopApp\icons\renvault.png'))
$assets = @(
    @('StoreLogo.png', 50), @('StoreLogo.scale-100.png', 50), @('StoreLogo.scale-200.png', 100),
    @('Square44x44Logo.png', 44), @('Square44x44Logo.scale-100.png', 44), @('Square44x44Logo.scale-200.png', 88),
    @('Square150x150Logo.png', 150), @('Square150x150Logo.scale-100.png', 150), @('Square150x150Logo.scale-200.png', 300)
)
foreach ($size in 16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 256) {
    $assets += , @("Square44x44Logo.targetsize-$size.png", $size)
    $assets += , @("Square44x44Logo.targetsize-${size}_altform-unplated.png", $size)
    $assets += , @("Square44x44Logo.targetsize-${size}_altform-lightunplated.png", $size)
}
foreach ($asset in $assets) {
    $bitmap = New-Object System.Drawing.Bitmap $asset[1], $asset[1]
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($icon, 0, 0, $asset[1], $asset[1])
    $bitmap.Save((Join-Path $stage "Assets\$($asset[0])"), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}
$icon.Dispose()

$makepri = Join-Path $tools 'makepri.exe'
$priconfig = Join-Path $work 'priconfig.xml'
& $makepri createconfig /cf $priconfig /dq en-US /pv 10.0.0 /o
if ($LASTEXITCODE -ne 0) { throw "makepri createconfig failed with $LASTEXITCODE" }
$config = [xml](Get-Content $priconfig -Raw)
$packaging = $config.resources.SelectSingleNode('packaging')
if ($packaging) { [void]$config.resources.RemoveChild($packaging) }
$config.Save($priconfig)
& $makepri new /pr $stage /cf $priconfig /mn (Join-Path $stage 'AppxManifest.xml') /of (Join-Path $stage 'resources.pri') /o
if ($LASTEXITCODE -ne 0) { throw "makepri new failed with $LASTEXITCODE" }

Copy-Item -Path (Join-Path $stage 'Assets') -Destination $AppImage -Recurse -Force
Copy-Item -Path (Join-Path $stage 'resources.pri') -Destination $AppImage -Force

$exe = Join-Path $AppImage "$appName.exe"
Set-ItemProperty -Path $exe -Name IsReadOnly -Value $false
& $mt -nologo -inputresource:"$exe;#1" -manifest $exeManifest -outputresource:"$exe;#1"
if ($LASTEXITCODE -ne 0) { throw "mt.exe failed with $LASTEXITCODE" }

$cfg = Join-Path $AppImage "app\$appName.cfg"
Set-ItemProperty -Path $cfg -Name IsReadOnly -Value $false
$lines = [IO.File]::ReadAllLines($cfg)
if ($lines -notcontains 'win.norestart=true') {
    $index = [array]::IndexOf($lines, '[Application]')
    if ($index -lt 0) { throw "$appName.cfg has no [Application] section" }
    $lines = $lines[0..$index] + 'win.norestart=true' + $lines[($index + 1)..($lines.Length - 1)]
    [IO.File]::WriteAllLines($cfg, $lines)
}

$pfx = Join-Path $work 'signing.pfx'
[IO.File]::WriteAllBytes($pfx, [Convert]::FromBase64String($env:WINDOWS_CERT_PFX))
try {
    $msix = Join-Path $AppImage "$appName.msix"
    & $makeappx pack /o /nv /d $stage /p $msix
    if ($LASTEXITCODE -ne 0) { throw "makeappx failed with $LASTEXITCODE" }
    & $signtool sign /q /fd SHA256 /f $pfx /p $env:WINDOWS_CERT_PASSWORD $msix
    if ($LASTEXITCODE -ne 0) { throw "signtool failed with $LASTEXITCODE" }

    $certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($pfx, $env:WINDOWS_CERT_PASSWORD)
    [IO.File]::WriteAllBytes((Join-Path $AppImage "$appName.cer"), $certificate.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
} finally {
    Remove-Item $pfx -Force -ErrorAction SilentlyContinue
}
