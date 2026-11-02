param(
    [Parameter(Mandatory = $true)][string]$Msi
)

$ErrorActionPreference = 'Stop'

$action = 'RenVaultRemovePasskeyPackage'
$condition = 'REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE'
$command = '"[System64Folder]WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -WindowStyle Hidden -Command "Get-AppxPackage -AllUsers -Name io.lauren.renvault | Remove-AppxPackage -AllUsers"'
$type = 34 + 64 + 1024 + 2048

$installer = New-Object -ComObject WindowsInstaller.Installer

function Invoke-Member($target, [string]$name, [object[]]$arguments) {
    $target.GetType().InvokeMember($name, 'InvokeMethod', $null, $target, $arguments)
}

function Get-Values($database, [string]$query, [int]$field) {
    $view = Invoke-Member $database 'OpenView' @($query)
    Invoke-Member $view 'Execute' @() | Out-Null
    $values = @()
    while ($true) {
        $record = Invoke-Member $view 'Fetch' @()
        if ($null -eq $record) { break }
        $values += $record.GetType().InvokeMember('StringData', 'GetProperty', $null, $record, @($field))
    }
    Invoke-Member $view 'Close' @() | Out-Null
    return $values
}

function Invoke-Sql($database, [string]$query) {
    $view = Invoke-Member $database 'OpenView' @($query)
    Invoke-Member $view 'Execute' @() | Out-Null
    Invoke-Member $view 'Close' @() | Out-Null
}

$database = Invoke-Member $installer 'OpenDatabase' @($Msi, 1)

if ((Get-Values $database "SELECT ``Action`` FROM ``CustomAction`` WHERE ``Action`` = '$action'" 1).Count -gt 0) {
    Write-Host 'The passkey cleanup action is already present'
    exit 0
}

if (@(Get-Values $database "SELECT ``Action`` FROM ``CustomAction`` WHERE ``Action`` = '$action'" 1).Count -gt 0) {
    Write-Host 'The passkey cleanup action is already present'
    exit 0
}

$directories = @(Get-Values $database 'SELECT `Directory` FROM `Directory`' 1)
$source = if ($directories -contains 'INSTALLDIR') { 'INSTALLDIR' } else { 'TARGETDIR' }

$used = @(
    Get-Values $database 'SELECT `Sequence` FROM `InstallExecuteSequence`' 1 |
        ForEach-Object { [int]$_ }
)

$removeFilesValues = @(
    Get-Values $database "SELECT ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'RemoveFiles'" 1
)

if ($removeFilesValues.Count -ne 1) {
    throw "Expected exactly one RemoveFiles action, found $($removeFilesValues.Count)"
}

$removeFiles = [int]$removeFilesValues[0]
$sequence = $removeFiles - 1
while ($used -contains $sequence) { $sequence-- }

Invoke-Sql $database "INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('$action', $type, '$source', '$command')"
Invoke-Sql $database "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('$action', '$condition', $sequence)"
Invoke-Member $database 'Commit' @() | Out-Null

Write-Host "Added $action at sequence $sequence (working directory $source)"
