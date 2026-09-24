param(
    [string]$Region = "us-east-1",
    [string]$InstanceName = "saccos-lms-prod",
    [string]$StaticIpName = "saccos-lms-prod-ip",
    [string]$BundleId = "small_3_0",
    [string]$BlueprintId = "amazon_linux_2023",
    [string]$AvailabilityZone = "",
    [string]$PublicDomain = "lms.foresight.co.tz",
    [string]$AcmeEmail = "davidginono625@gmail.com",
    [string]$KeyPairName = "saccos-lms-prod-key",
    [string]$KeyFile = "",
    [string]$RemoteUser = "ec2-user",
    [string]$PostgresPassword = $env:POSTGRES_PASSWORD,
    [string]$AppSecretsEncryptionKey = $env:APP_SECRETS_ENCRYPTION_KEY,
    [string]$AppTimeZone = $env:APP_TIME_ZONE,
    [string]$SpringMailHost = $env:SPRING_MAIL_HOST,
    [string]$SpringMailPort = $env:SPRING_MAIL_PORT,
    [string]$SpringMailUsername = $env:SPRING_MAIL_USERNAME,
    [string]$SpringMailPassword = $env:SPRING_MAIL_PASSWORD,
    [string]$AppMailFromAddress = $env:APP_MAIL_FROM_ADDRESS,
    [string]$AppSmsEnabled = $env:APP_SMS_ENABLED,
    [string]$AppSmsBenterBaseUrl = $env:APP_SMS_BENTER_BASE_URL,
    [string]$AppSmsBenterClientId = $env:APP_SMS_BENTER_CLIENT_ID,
    [string]$AppSmsBenterApiKey = $env:APP_SMS_BENTER_API_KEY,
    [string]$AppSmsBenterSenderId = $env:APP_SMS_BENTER_SENDER_ID,
    [string]$ExternalForesightBaseUrl = $env:EXTERNAL_FORESIGHT_BASE_URL,
    [string]$ExternalMemberPortalBaseUrl = $env:EXTERNAL_MEMBERPORTAL_BASE_URL
)

$ErrorActionPreference = "Stop"

function Invoke-AwsJson {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & aws @Arguments --output json
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI failed: aws $($Arguments -join ' ')"
    }
    if ([string]::IsNullOrWhiteSpace($output)) {
        return $null
    }
    return $output | ConvertFrom-Json
}

function Invoke-AwsText {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & aws @Arguments --output text
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI failed: aws $($Arguments -join ' ')"
    }
    return $output
}

function New-Secret {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($buffer)
}

function Write-EnvLine {
    param([System.Text.StringBuilder]$Builder, [string]$Name, [string]$Value)
    if ($null -eq $Value) {
        $Value = ""
    }
    $escaped = $Value.Replace("\", "\\").Replace("`r", "").Replace("`n", "\n").Replace('"', '\"')
    [void]$Builder.AppendLine("$Name=""$escaped""")
}

function Get-EnvValue {
    param([string]$Text, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ""
    }
    $pattern = "(?m)^" + [regex]::Escape($Name) + "=""(.*)""$"
    $match = [regex]::Match($Text, $pattern)
    if (-not $match.Success) {
        return ""
    }
    return $match.Groups[1].Value.Replace("\n", "`n").Replace('\"', '"').Replace("\\", "\")
}

function Test-Base64Key32 {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }
    try {
        return ([Convert]::FromBase64String($Value).Length -eq 32)
    } catch {
        return $false
    }
}

function Protect-KeyFile {
    param([string]$Path)
    if ($IsWindows -or $env:OS -eq "Windows_NT") {
        & icacls $Path /inheritance:r *> $null
        & icacls $Path /grant:r "$($env:USERNAME):R" *> $null
    } else {
        & chmod 600 $Path
    }
}

function Ensure-KeyPair {
    param([string]$Name, [string]$Path)

    if (Test-Path -LiteralPath $Path) {
        Protect-KeyFile -Path $Path
        return
    }

    $existing = Invoke-AwsJson @("lightsail", "get-key-pairs", "--region", $Region)
    $match = $existing.keyPairs | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($match) {
        throw "Lightsail key pair '$Name' already exists, but '$Path' is missing. Place the private key there or pass -KeyPairName/-KeyFile for a new key."
    }

    $created = Invoke-AwsJson @("lightsail", "create-key-pair", "--region", $Region, "--key-pair-name", $Name)
    if ($created.privateKeyBase64 -like "*BEGIN*PRIVATE KEY*") {
        $pem = $created.privateKeyBase64
    } else {
        $pem = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($created.privateKeyBase64))
    }
    Set-Content -LiteralPath $Path -Value $pem -NoNewline
    Protect-KeyFile -Path $Path
}

function Wait-InstanceRunning {
    param([string]$Name)

    for ($i = 0; $i -lt 60; $i++) {
        $instance = Invoke-AwsJson @("lightsail", "get-instance", "--region", $Region, "--instance-name", $Name)
        if ($instance.instance.state.name -eq "running") {
            return $instance.instance
        }
        Start-Sleep -Seconds 10
    }
    throw "Lightsail instance '$Name' did not become running within 10 minutes."
}

function Wait-SshReady {
    param([string]$HostName, [string]$KeyPath)

    for ($i = 0; $i -lt 60; $i++) {
        & ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=8 -i $KeyPath "$RemoteUser@$HostName" "echo ready" *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 10
    }
    throw "SSH did not become ready on $HostName."
}

if ([string]::IsNullOrWhiteSpace($KeyFile)) {
    $KeyFile = Join-Path $PSScriptRoot "$KeyPairName.pem"
}

$PostgresPasswordProvided = -not [string]::IsNullOrWhiteSpace($PostgresPassword)
$AppSecretsEncryptionKeyProvided = -not [string]::IsNullOrWhiteSpace($AppSecretsEncryptionKey)

if ([string]::IsNullOrWhiteSpace($PostgresPassword)) {
    $PostgresPassword = New-Secret
}
if ([string]::IsNullOrWhiteSpace($AppSecretsEncryptionKey)) {
    $AppSecretsEncryptionKey = New-Secret -Bytes 32
}
if ([string]::IsNullOrWhiteSpace($SpringMailPort)) {
    $SpringMailPort = "465"
}
if ([string]::IsNullOrWhiteSpace($AppTimeZone)) {
    $AppTimeZone = "Africa/Nairobi"
}
if ([string]::IsNullOrWhiteSpace($AppSmsEnabled)) {
    $AppSmsEnabled = "true"
}
if ([string]::IsNullOrWhiteSpace($ExternalForesightBaseUrl)) {
    $ExternalForesightBaseUrl = "https://api.foresightfin.app"
}
if ([string]::IsNullOrWhiteSpace($ExternalMemberPortalBaseUrl)) {
    $ExternalMemberPortalBaseUrl = $ExternalForesightBaseUrl
}

$identity = Invoke-AwsJson @("sts", "get-caller-identity", "--region", $Region)
if ($identity.Arn -notlike "*:user/david") {
    throw "AWS CLI is authenticated as '$($identity.Arn)', not IAM user 'david'."
}

$bundles = Invoke-AwsJson @("lightsail", "get-bundles", "--region", $Region)
$bundle = $bundles.bundles | Where-Object { $_.bundleId -eq $BundleId } | Select-Object -First 1
if (-not $bundle) {
    throw "Lightsail bundle '$BundleId' was not found in $Region."
}
if ($bundle.price -gt 12) {
    throw "Bundle '$BundleId' is priced at $($bundle.price) USD/month. Use small_3_0 for the practical ~$12 budget."
}
if ($BundleId -like "*ipv6*") {
    throw "Refusing IPv6-only bundle '$BundleId'. This deployment needs public IPv4 browser access."
}

if ([string]::IsNullOrWhiteSpace($AvailabilityZone)) {
    $zones = Invoke-AwsJson @("lightsail", "get-regions", "--include-availability-zones")
    $regionData = $zones.regions | Where-Object { $_.name -eq $Region } | Select-Object -First 1
    $AvailabilityZone = ($regionData.availabilityZones | Where-Object { $_.state -eq "available" } | Select-Object -First 1).zoneName
    if ([string]::IsNullOrWhiteSpace($AvailabilityZone)) {
        $AvailabilityZone = "${Region}a"
    }
}

Write-Host "Deploying $InstanceName in $Region/$AvailabilityZone as $($identity.Arn)"
Write-Host "Using Lightsail bundle $BundleId ($($bundle.ramSizeInGb) GB RAM, $($bundle.price) USD/month)"

Ensure-KeyPair -Name $KeyPairName -Path $KeyFile

$instances = Invoke-AwsJson @("lightsail", "get-instances", "--region", $Region)
$instance = $instances.instances | Where-Object { $_.name -eq $InstanceName } | Select-Object -First 1
if (-not $instance) {
    $userData = @"
#!/bin/bash
set -euo pipefail
dnf update -y
dnf install -y docker tar gzip
systemctl enable --now docker
usermod -aG docker ec2-user || true
mkdir -p /mnt/saccos-data/postgres /mnt/saccos-data/uploads /mnt/saccos-data/saccos /mnt/saccos-data/caddy-data /mnt/saccos-data/caddy-config /opt/saccos-lms
chown -R 100:101 /mnt/saccos-data/uploads /mnt/saccos-data/saccos
chmod -R u+rwX,g+rwX,o-rwx /mnt/saccos-data/uploads /mnt/saccos-data/saccos
chown -R ec2-user:ec2-user /opt/saccos-lms
"@
    $userDataFile = New-TemporaryFile
    Set-Content -LiteralPath $userDataFile -Value $userData -NoNewline
    try {
        Invoke-AwsJson @(
            "lightsail", "create-instances",
            "--region", $Region,
            "--instance-names", $InstanceName,
            "--availability-zone", $AvailabilityZone,
            "--blueprint-id", $BlueprintId,
            "--bundle-id", $BundleId,
            "--key-pair-name", $KeyPairName,
            "--user-data", "file://$userDataFile",
            "--tags", "key=App,value=saccos-lms", "key=Environment,value=prod"
        ) *> $null
    } finally {
        Remove-Item -LiteralPath $userDataFile -Force
    }
}

$instance = Wait-InstanceRunning -Name $InstanceName

$staticIps = Invoke-AwsJson @("lightsail", "get-static-ips", "--region", $Region)
$staticIp = $staticIps.staticIps | Where-Object { $_.name -eq $StaticIpName } | Select-Object -First 1
if (-not $staticIp) {
    Invoke-AwsJson @("lightsail", "allocate-static-ip", "--region", $Region, "--static-ip-name", $StaticIpName) *> $null
    $staticIps = Invoke-AwsJson @("lightsail", "get-static-ips", "--region", $Region)
    $staticIp = $staticIps.staticIps | Where-Object { $_.name -eq $StaticIpName } | Select-Object -First 1
}
if ($staticIp.attachedTo -ne $InstanceName) {
    Invoke-AwsJson @("lightsail", "attach-static-ip", "--region", $Region, "--static-ip-name", $StaticIpName, "--instance-name", $InstanceName) *> $null
}
$publicIp = $staticIp.ipAddress

foreach ($port in @(80, 443)) {
    Invoke-AwsJson @(
        "lightsail", "open-instance-public-ports",
        "--region", $Region,
        "--instance-name", $InstanceName,
        "--port-info", "fromPort=$port,toPort=$port,protocol=tcp,cidrs=0.0.0.0/0"
    ) *> $null
}

Wait-SshReady -HostName $publicIp -KeyPath $KeyFile

$remoteEnv = (& ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $KeyFile "$RemoteUser@$publicIp" "sudo cat /opt/saccos-lms/current/.env 2>/dev/null || true") -join "`n"
if (-not $PostgresPasswordProvided) {
    $existingPostgresPassword = Get-EnvValue -Text $remoteEnv -Name "POSTGRES_PASSWORD"
    if (-not [string]::IsNullOrWhiteSpace($existingPostgresPassword)) {
        $PostgresPassword = $existingPostgresPassword
    }
}
if (-not $AppSecretsEncryptionKeyProvided) {
    $existingEncryptionKey = Get-EnvValue -Text $remoteEnv -Name "APP_SECRETS_ENCRYPTION_KEY"
    if (Test-Base64Key32 -Value $existingEncryptionKey) {
        $AppSecretsEncryptionKey = $existingEncryptionKey
    }
}

$envBuilder = [System.Text.StringBuilder]::new()
Write-EnvLine $envBuilder "APP_PUBLIC_DOMAIN" $PublicDomain
Write-EnvLine $envBuilder "ACME_EMAIL" $AcmeEmail
Write-EnvLine $envBuilder "POSTGRES_DB" "sacco"
Write-EnvLine $envBuilder "POSTGRES_USER" "sacco"
Write-EnvLine $envBuilder "POSTGRES_PASSWORD" $PostgresPassword
Write-EnvLine $envBuilder "APP_BASE_URL" "https://$PublicDomain"
Write-EnvLine $envBuilder "APP_TIME_ZONE" $AppTimeZone
Write-EnvLine $envBuilder "APP_SECRETS_ENCRYPTION_KEY" $AppSecretsEncryptionKey
Write-EnvLine $envBuilder "SPRING_MAIL_HOST" $SpringMailHost
Write-EnvLine $envBuilder "SPRING_MAIL_PORT" $SpringMailPort
Write-EnvLine $envBuilder "SPRING_MAIL_USERNAME" $SpringMailUsername
Write-EnvLine $envBuilder "SPRING_MAIL_PASSWORD" $SpringMailPassword
Write-EnvLine $envBuilder "APP_MAIL_FROM_ADDRESS" $AppMailFromAddress
Write-EnvLine $envBuilder "APP_SMS_ENABLED" $AppSmsEnabled
Write-EnvLine $envBuilder "APP_SMS_BENTER_BASE_URL" $AppSmsBenterBaseUrl
Write-EnvLine $envBuilder "APP_SMS_BENTER_CLIENT_ID" $AppSmsBenterClientId
Write-EnvLine $envBuilder "APP_SMS_BENTER_API_KEY" $AppSmsBenterApiKey
Write-EnvLine $envBuilder "APP_SMS_BENTER_SENDER_ID" $AppSmsBenterSenderId
Write-EnvLine $envBuilder "EXTERNAL_FORESIGHT_BASE_URL" $ExternalForesightBaseUrl
Write-EnvLine $envBuilder "EXTERNAL_MEMBERPORTAL_BASE_URL" $ExternalMemberPortalBaseUrl
Write-EnvLine $envBuilder "SERVER_SERVLET_SESSION_COOKIE_SECURE" "true"
Write-EnvLine $envBuilder "SERVER_FORWARD_HEADERS_STRATEGY" "framework"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$archive = Join-Path (Get-Location) ".tmp-lightsail-$stamp.tgz"
$envFile = Join-Path (Get-Location) ".tmp-lightsail-$stamp.env"
try {
    Set-Content -LiteralPath $envFile -Value $envBuilder.ToString() -NoNewline
    & tar --exclude=.git --exclude=target --exclude=node_modules --exclude=.idea --exclude=.vscode --exclude=.tmp-lightsail-*.tgz --exclude=.tmp-lightsail-*.env -czf $archive .
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create deployment archive."
    }

    & ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $KeyFile "$RemoteUser@$publicIp" "sudo mkdir -p /opt/saccos-lms/releases/$stamp && sudo chown -R $RemoteUser`:$RemoteUser /opt/saccos-lms"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to prepare remote release directory."
    }
    & scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $KeyFile $archive "$RemoteUser@$publicIp`:/tmp/saccos-lms.tgz"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload deployment archive."
    }
    & scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $KeyFile $envFile "$RemoteUser@$publicIp`:/tmp/saccos-lms.env"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload production env file."
    }

    $remote = @"
set -euo pipefail
sudo dnf install -y docker tar gzip
sudo systemctl enable --now docker
if ! sudo docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64" -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi
sudo mkdir -p /mnt/saccos-data/postgres /mnt/saccos-data/uploads /mnt/saccos-data/saccos /mnt/saccos-data/caddy-data /mnt/saccos-data/caddy-config
sudo chown -R 100:101 /mnt/saccos-data/uploads /mnt/saccos-data/saccos
sudo chmod -R u+rwX,g+rwX,o-rwx /mnt/saccos-data/uploads /mnt/saccos-data/saccos
sudo chown -R ec2-user:ec2-user /opt/saccos-lms
rm -rf /opt/saccos-lms/releases/$stamp
mkdir -p /opt/saccos-lms/releases/$stamp
tar -xzf /tmp/saccos-lms.tgz -C /opt/saccos-lms/releases/$stamp
mv /tmp/saccos-lms.env /opt/saccos-lms/releases/$stamp/.env
chmod 600 /opt/saccos-lms/releases/$stamp/.env
ln -sfn /opt/saccos-lms/releases/$stamp /opt/saccos-lms/current
cd /opt/saccos-lms/current
sudo docker compose --env-file .env -f deploy/aws/docker-compose.lightsail.yml up --build -d --remove-orphans
sudo docker compose --env-file .env -f deploy/aws/docker-compose.lightsail.yml ps
for i in {1..60}; do
  if sudo docker compose --env-file .env -f deploy/aws/docker-compose.lightsail.yml exec -T app wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Local health check passed"
    exit 0
  fi
  sleep 5
done
sudo docker compose --env-file .env -f deploy/aws/docker-compose.lightsail.yml logs --tail=120 app
exit 1
"@
    & ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $KeyFile "$RemoteUser@$publicIp" $remote
    if ($LASTEXITCODE -ne 0) {
        throw "Remote deployment failed."
    }
} finally {
    if (Test-Path -LiteralPath $archive) {
        Remove-Item -LiteralPath $archive -Force
    }
    if (Test-Path -LiteralPath $envFile) {
        Remove-Item -LiteralPath $envFile -Force
    }
}

Write-Host "Deployment finished."
Write-Host "Static IP: $publicIp"
Write-Host "Point Cloudflare A record $PublicDomain to $publicIp, then wait for Caddy HTTPS."
Write-Host "Endpoint: https://$PublicDomain"
