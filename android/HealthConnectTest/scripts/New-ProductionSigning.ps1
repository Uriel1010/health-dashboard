param([Parameter(Mandatory=$true)][string]$Directory)
$ErrorActionPreference = 'Stop'
$signingDirectory = [IO.Path]::GetFullPath($Directory)
if (Test-Path -LiteralPath $signingDirectory) { throw 'Signing directory already exists; refusing to replace a signing identity.' }
New-Item -ItemType Directory -Path $signingDirectory | Out-Null
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls $signingDirectory /inheritance:r /grant:r "${account}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes)
$keystore = Join-Path $signingDirectory 'health-companion-production.keystore'
$env:HEALTH_NEW_SIGNING_PASSWORD = $password
try {
    $keytool = (Get-Command keytool -ErrorAction Stop).Source
    & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keystore -alias health-companion -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Health Companion, OU=Android, O=Uriel1010' -storepass:env HEALTH_NEW_SIGNING_PASSWORD -keypass:env HEALTH_NEW_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    $keyPath = $keystore.Replace('\', '/')
    $properties = "storeFile=$keyPath`nstorePassword=$password`nkeyAlias=health-companion`nkeyPassword=$password`n"
    [IO.File]::WriteAllText((Join-Path $signingDirectory 'signing.properties'), $properties, [Text.UTF8Encoding]::new($false))
    Write-Output "Production signing identity created outside the repository: $signingDirectory"
} finally {
    Remove-Item Env:HEALTH_NEW_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $rng.Dispose()
}
