param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Apk = "$PSScriptRoot\..\app\build\outputs\apk\release\app-release.apk",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$OutputDirectory = "$PSScriptRoot\..\.release-diagnostics\startup-test"
)
$ErrorActionPreference = 'Stop'
# This test deliberately clears app data. Never run it on a user's phone.
if ($Serial -notmatch '^emulator-\d+$') { throw 'Use an isolated emulator serial; physical devices are not allowed.' }
$package = 'com.uriel.healthconnecttest'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
function Invoke-TestAdb {
    $result = & $Adb -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
Invoke-TestAdb install -r $Apk | Out-Host
Invoke-TestAdb shell pm clear $package | Out-Host
Invoke-TestAdb logcat -b crash -c | Out-Null
for ($attempt = 1; $attempt -le 3; $attempt++) {
    Invoke-TestAdb shell am force-stop $package | Out-Null
    Invoke-TestAdb shell am start -W -n "$package/.MainActivity" | Out-Host
    Start-Sleep -Seconds 3
    $crashes = Invoke-TestAdb logcat -b crash -d -v threadtime
    $crashes | Set-Content -LiteralPath (Join-Path $OutputDirectory 'crash-log.txt')
    if (($crashes -join "`n") -match 'FATAL EXCEPTION|Fatal signal') { throw 'Production startup crashed; see crash-log.txt.' }
    Invoke-TestAdb shell pidof $package | Out-Null
    Write-Host "PASS: production cold launch $attempt, process survives and crash buffer is clean."
}
function Get-TestUi {
    Invoke-TestAdb shell uiautomator dump /sdcard/health-startup-test.xml | Out-Null
    return [xml]((Invoke-TestAdb shell cat /sdcard/health-startup-test.xml) -join "`n")
}
foreach ($tab in @('Food', 'Trends', 'Health', 'Settings', 'Today')) {
    $ui = Get-TestUi
    $node = $ui.SelectSingleNode("//node[@content-desc='$tab' or @text='$tab']")
    if ($null -eq $node) { throw "Missing navigation destination: $tab" }
    $bounds = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $tapX = [int](($bounds[0] + $bounds[2]) / 2)
    $tapY = [int](($bounds[1] + $bounds[3]) / 2)
    Invoke-TestAdb shell input tap $tapX $tapY | Out-Null
    $ui = Get-TestUi
    $ui.Save((Join-Path (Resolve-Path $OutputDirectory) "$tab.xml"))
    Invoke-TestAdb shell pidof $package | Out-Null
    Invoke-TestAdb shell screencap -p /sdcard/health-startup-test.png | Out-Null
    Invoke-TestAdb pull /sdcard/health-startup-test.png (Join-Path $OutputDirectory "$tab.png") | Out-Null
    Write-Host "PASS: $tab navigation rendered."
}
$crashes = Invoke-TestAdb logcat -b crash -d -v threadtime
if (($crashes -join "`n") -match 'FATAL EXCEPTION|Fatal signal') { throw 'Crash during navigation.' }
Write-Host 'PASS: signed production APK cold-start and navigation smoke test.'
