param(
    [string]$Region = "us-east-1",
    [string]$AppName = "saccos-lms",
    [string]$Environment = "prod",
    [string]$InstanceType = "t3.micro",
    [string]$DbInstanceClass = "db.t4g.micro",
    [string]$DbEngineVersion = "16.3",
    [int]$DbBackupRetentionDays = 1,
    [string]$PublicDomain = $env:APP_PUBLIC_DOMAIN,
    [switch]$OverwriteExistingConfig
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PublicDomain)) {
    $PublicDomain = "microfinance.example.com"
}

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

function New-Password {
    $chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!#$%*-_=+"
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    -join ($bytes | ForEach-Object { $chars[[int]$_ % $chars.Length] })
}

function Put-Parameter {
    param(
        [string]$Name,
        [string]$Value,
        [string]$Type = "String",
        [switch]$Force
    )

    $exists = $false
    try {
        & aws ssm get-parameter --region $Region --name $Name --with-decryption *> $null
        $exists = $LASTEXITCODE -eq 0
    } catch {
        $exists = $false
    }

    if ($exists -and -not $Force -and -not $OverwriteExistingConfig) {
        Write-Host "Keeping existing SSM parameter $Name"
        return
    }

    $args = @(
        "ssm", "put-parameter",
        "--region", $Region,
        "--name", $Name,
        "--type", $Type,
        "--value", $Value,
        "--tier", "Standard"
    )
    if ($exists -or $Force -or $OverwriteExistingConfig) {
        $args += "--overwrite"
    }
    & aws @args *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to write SSM parameter $Name"
    }
}

function Ensure-Role {
    param([string]$RoleName, [string]$PrefixArn)

    try {
        & aws iam get-role --role-name $RoleName *> $null
        $roleExists = $LASTEXITCODE -eq 0
    } catch {
        $roleExists = $false
    }

    if (-not $roleExists) {
        $trust = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
        $trustFile = New-TemporaryFile
        Set-Content -LiteralPath $trustFile -Value $trust -NoNewline
        & aws iam create-role --role-name $RoleName --assume-role-policy-document "file://$trustFile" *> $null
        Remove-Item -LiteralPath $trustFile -Force
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create IAM role $RoleName"
        }
    }

    & aws iam attach-role-policy --role-name $RoleName --policy-arn "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore" *> $null
    & aws iam attach-role-policy --role-name $RoleName --policy-arn "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly" *> $null

    $prefixRootArn = $PrefixArn.TrimEnd("/")
    $policy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": [
        "$prefixRootArn",
        "$PrefixArn*"
      ]
    }
  ]
}
"@
    $policyFile = New-TemporaryFile
    Set-Content -LiteralPath $policyFile -Value $policy -NoNewline
    & aws iam put-role-policy --role-name $RoleName --policy-name "$RoleName-ssm-parameters" --policy-document "file://$policyFile" *> $null
    Remove-Item -LiteralPath $policyFile -Force
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to attach inline SSM policy to $RoleName"
    }
}

function Ensure-InstanceProfile {
    param([string]$ProfileName, [string]$RoleName)

    try {
        & aws iam get-instance-profile --instance-profile-name $ProfileName *> $null
        $profileExists = $LASTEXITCODE -eq 0
    } catch {
        $profileExists = $false
    }

    if (-not $profileExists) {
        & aws iam create-instance-profile --instance-profile-name $ProfileName *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create instance profile $ProfileName"
        }
    }

    $profile = Invoke-AwsJson @("iam", "get-instance-profile", "--instance-profile-name", $ProfileName)
    $hasRole = $profile.InstanceProfile.Roles | Where-Object { $_.RoleName -eq $RoleName }
    if (-not $hasRole) {
        & aws iam add-role-to-instance-profile --instance-profile-name $ProfileName --role-name $RoleName *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to add role $RoleName to instance profile $ProfileName"
        }
        Start-Sleep -Seconds 12
    }
}

function Ensure-SecurityGroup {
    param([string]$Name, [string]$Description, [string]$VpcId)

    $groups = Invoke-AwsJson @(
        "ec2", "describe-security-groups",
        "--region", $Region,
        "--filters", "Name=vpc-id,Values=$VpcId", "Name=group-name,Values=$Name"
    )
    if ($groups.SecurityGroups.Count -gt 0) {
        return $groups.SecurityGroups[0].GroupId
    }

    $groupId = Invoke-AwsText @(
        "ec2", "create-security-group",
        "--region", $Region,
        "--group-name", $Name,
        "--description", $Description,
        "--vpc-id", $VpcId,
        "--query", "GroupId"
    )
    & aws ec2 create-tags --region $Region --resources $groupId --tags "Key=Name,Value=$Name" "Key=App,Value=$AppName" "Key=Environment,Value=$Environment" *> $null
    return $groupId
}

function Add-IngressIfMissing {
    param([string]$GroupId, [int]$Port, [string]$Cidr, [string]$SourceGroupId)

    $group = Invoke-AwsJson @("ec2", "describe-security-groups", "--region", $Region, "--group-ids", $GroupId)
    foreach ($permission in $group.SecurityGroups[0].IpPermissions) {
        if ($permission.IpProtocol -ne "tcp" -or $permission.FromPort -ne $Port -or $permission.ToPort -ne $Port) {
            continue
        }
        if ($SourceGroupId) {
            $match = $permission.UserIdGroupPairs | Where-Object { $_.GroupId -eq $SourceGroupId }
            if ($match) {
                return
            }
        } else {
            $match = $permission.IpRanges | Where-Object { $_.CidrIp -eq $Cidr }
            if ($match) {
                return
            }
        }
    }

    $args = @("ec2", "authorize-security-group-ingress", "--region", $Region, "--group-id", $GroupId, "--ip-permissions")
    if ($SourceGroupId) {
        $permission = "IpProtocol=tcp,FromPort=$Port,ToPort=$Port,UserIdGroupPairs=[{GroupId=$SourceGroupId}]"
    } else {
        $permission = "IpProtocol=tcp,FromPort=$Port,ToPort=$Port,IpRanges=[{CidrIp=$Cidr}]"
    }
    & aws @args $permission *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to add ingress rule for $GroupId port $Port"
    }
}

$stackSlug = "$AppName-$Environment"
$parameterPrefix = "/$AppName/$Environment"
$repositoryName = $AppName
$roleName = "$stackSlug-ec2-role"
$profileName = "$stackSlug-instance-profile"
$webSgName = "$stackSlug-web-sg"
$dbSgName = "$stackSlug-db-sg"
$dbSubnetGroupName = "$stackSlug-db-subnets"
$dbIdentifier = "$stackSlug-db"
$dbName = "sacco"
$dbUsername = "sacco_admin"

$identity = Invoke-AwsJson @("sts", "get-caller-identity", "--region", $Region)
$accountId = $identity.Account
$partition = "aws"
$prefixArn = "arn:${partition}:ssm:${Region}:$accountId`:parameter$parameterPrefix/"

Write-Host "Deploying $stackSlug to account $accountId in $Region"

$ecrOutput = & aws ecr describe-repositories --region $Region --repository-names $repositoryName --output json 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ecrOutput)) {
    $ecrOutput = & aws ecr create-repository --region $Region --repository-name $repositoryName --image-scanning-configuration scanOnPush=true --output json
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ecrOutput)) {
        throw "Failed to create or describe ECR repository $repositoryName"
    }
}
$ecr = $ecrOutput | ConvertFrom-Json
$repositoryUri = $ecr.repositories[0].repositoryUri

$tag = (git rev-parse --short HEAD 2>$null)
if ([string]::IsNullOrWhiteSpace($tag)) {
    $tag = Get-Date -Format "yyyyMMddHHmmss"
}
$imageUri = "$repositoryUri`:$tag"

Write-Host "Building Docker image $imageUri"
& aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $repositoryUri.Split("/")[0]
if ($LASTEXITCODE -ne 0) {
    throw "Docker login to ECR failed"
}
& docker build --platform linux/amd64 -t $imageUri .
if ($LASTEXITCODE -ne 0) {
    throw "Docker image build failed"
}
& docker push $imageUri
if ($LASTEXITCODE -ne 0) {
    throw "Docker image push failed"
}

$vpcs = Invoke-AwsJson @("ec2", "describe-vpcs", "--region", $Region, "--filters", "Name=is-default,Values=true")
if ($vpcs.Vpcs.Count -eq 0) {
    throw "No default VPC found in $Region. Create/select a VPC before running this script."
}
$vpcId = $vpcs.Vpcs[0].VpcId
$subnetData = Invoke-AwsJson @("ec2", "describe-subnets", "--region", $Region, "--filters", "Name=vpc-id,Values=$vpcId")
$subnetIds = @($subnetData.Subnets | Sort-Object AvailabilityZone | Select-Object -ExpandProperty SubnetId)
if ($subnetIds.Count -lt 2) {
    throw "RDS needs at least two subnets in different AZs."
}

$webSgId = Ensure-SecurityGroup -Name $webSgName -Description "Microfinance LMS public web access" -VpcId $vpcId
$dbSgId = Ensure-SecurityGroup -Name $dbSgName -Description "Microfinance LMS RDS access from app host only" -VpcId $vpcId
Add-IngressIfMissing -GroupId $webSgId -Port 80 -Cidr "0.0.0.0/0"
Add-IngressIfMissing -GroupId $webSgId -Port 443 -Cidr "0.0.0.0/0"
Add-IngressIfMissing -GroupId $dbSgId -Port 5432 -SourceGroupId $webSgId

Ensure-Role -RoleName $roleName -PrefixArn $prefixArn
Ensure-InstanceProfile -ProfileName $profileName -RoleName $roleName

try {
    & aws rds describe-db-subnet-groups --region $Region --db-subnet-group-name $dbSubnetGroupName *> $null
    $subnetGroupExists = $LASTEXITCODE -eq 0
} catch {
    $subnetGroupExists = $false
}
if (-not $subnetGroupExists) {
    & aws rds create-db-subnet-group `
        --region $Region `
        --db-subnet-group-name $dbSubnetGroupName `
        --db-subnet-group-description "Microfinance LMS RDS subnets" `
        --subnet-ids $subnetIds *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create RDS subnet group"
    }
}

$dbCreated = $false
$dbPassword = $null
$db = $null
try {
    $db = Invoke-AwsJson @("rds", "describe-db-instances", "--region", $Region, "--db-instance-identifier", $dbIdentifier)
} catch {
    Write-Host "Creating RDS PostgreSQL instance $dbIdentifier"
    $dbPassword = New-Password
    & aws rds create-db-instance `
        --region $Region `
        --db-instance-identifier $dbIdentifier `
        --db-instance-class $DbInstanceClass `
        --engine postgres `
        --engine-version $DbEngineVersion `
        --allocated-storage 20 `
        --storage-type gp2 `
        --db-name $dbName `
        --master-username $dbUsername `
        --master-user-password $dbPassword `
        --vpc-security-group-ids $dbSgId `
        --db-subnet-group-name $dbSubnetGroupName `
        --backup-retention-period $DbBackupRetentionDays `
        --preferred-backup-window "03:00-03:30" `
        --preferred-maintenance-window "sun:04:00-sun:04:30" `
        --no-multi-az `
        --no-publicly-accessible `
        --deletion-protection `
        --copy-tags-to-snapshot `
        --tags "Key=Name,Value=$dbIdentifier" "Key=App,Value=$AppName" "Key=Environment,Value=$Environment" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create RDS instance"
    }
    $dbCreated = $true
}

Write-Host "Waiting for RDS to become available"
& aws rds wait db-instance-available --region $Region --db-instance-identifier $dbIdentifier
if ($LASTEXITCODE -ne 0) {
    throw "RDS did not become available"
}
$db = Invoke-AwsJson @("rds", "describe-db-instances", "--region", $Region, "--db-instance-identifier", $dbIdentifier)
$dbEndpoint = $db.DBInstances[0].Endpoint.Address

if ($db.DBInstances[0].MasterUsername -ne $dbUsername) {
    $dbUsername = $db.DBInstances[0].MasterUsername
}

if (-not $dbPassword) {
    try {
        $existingPassword = Invoke-AwsText @(
            "ssm", "get-parameter",
            "--region", $Region,
            "--name", "$parameterPrefix/SPRING_DATASOURCE_PASSWORD",
            "--with-decryption",
            "--query", "Parameter.Value"
        )
        if (-not [string]::IsNullOrWhiteSpace($existingPassword)) {
            $dbPassword = $existingPassword
        }
    } catch {
        throw "RDS instance $dbIdentifier already exists, but $parameterPrefix/SPRING_DATASOURCE_PASSWORD was not found. Refusing to guess or replace the live DB password."
    }
}

$addresses = Invoke-AwsJson @("ec2", "describe-addresses", "--region", $Region, "--filters", "Name=tag:Name,Values=$stackSlug-eip")
if ($addresses.Addresses.Count -gt 0) {
    $allocationId = $addresses.Addresses[0].AllocationId
    $publicIp = $addresses.Addresses[0].PublicIp
} else {
    $address = Invoke-AwsJson @("ec2", "allocate-address", "--region", $Region, "--domain", "vpc", "--tag-specifications", "ResourceType=elastic-ip,Tags=[{Key=Name,Value=$stackSlug-eip},{Key=App,Value=$AppName},{Key=Environment,Value=$Environment}]")
    $allocationId = $address.AllocationId
    $publicIp = $address.PublicIp
}
$fallbackDomain = "$($publicIp.Replace('.', '-')).sslip.io"
$domain = if ([string]::IsNullOrWhiteSpace($PublicDomain)) { $fallbackDomain } else { $PublicDomain.Trim().ToLowerInvariant() }
$baseUrl = "https://$domain"

$secretNames = @(
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_MAIL_PASSWORD",
    "GOOGLE_CLIENT_SECRET",
    "APP_SMS_BENTER_API_KEY",
    "APP_AUTH_LOCAL_DEV_MINOR_ADMIN_PASSWORD"
)

$applicationYml = Get-Content -Raw -LiteralPath "src/main/resources/application.yml"
$matches = [regex]::Matches($applicationYml, '\$\{([A-Z0-9_]+):([^}]*)\}')
$defaults = [ordered]@{}
foreach ($match in $matches) {
    $key = $match.Groups[1].Value
    $value = $match.Groups[2].Value
    if (-not $defaults.Contains($key)) {
        $defaults[$key] = $value
    }
}

foreach ($entry in $defaults.GetEnumerator()) {
    $type = if ($secretNames -contains $entry.Key) { "SecureString" } else { "String" }
    Put-Parameter -Name "$parameterPrefix/$($entry.Key)" -Value $entry.Value -Type $type
}

Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_URL" -Value "jdbc:postgresql://$dbEndpoint`:5432/$dbName" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_USERNAME" -Value $dbUsername -Type "String" -Force
if ($dbCreated -or $OverwriteExistingConfig) {
    Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_PASSWORD" -Value $dbPassword -Type "SecureString" -Force
} else {
    Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_PASSWORD" -Value $dbPassword -Type "SecureString"
}
Put-Parameter -Name "$parameterPrefix/APP_BASE_URL" -Value $baseUrl -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_SERVLET_SESSION_COOKIE_SECURE" -Value "true" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_FORWARD_HEADERS_STRATEGY" -Value "framework" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_SERVLET_JSP_DEVELOPMENT" -Value "false" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_DEVTOOLS_RESTART_ENABLED" -Value "false" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_DEVTOOLS_LIVERELOAD_ENABLED" -Value "false" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE" -Value "5" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE" -Value "1" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_TOMCAT_THREADS_MAX" -Value "80" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_TOMCAT_THREADS_MIN_SPARE" -Value "5" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_TOMCAT_MAX_CONNECTIONS" -Value "1000" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SERVER_TOMCAT_ACCEPT_COUNT" -Value "100" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/SPRING_WEB_RESOURCES_CACHE_PERIOD" -Value "365d" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/JAVA_TOOL_OPTIONS" -Value "-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" -Type "String" -Force
Put-Parameter -Name "$parameterPrefix/CATALINA_OPTS" -Value "-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseG1GC" -Type "String" -Force

$amiId = Invoke-AwsText @(
    "ssm", "get-parameter",
    "--region", $Region,
    "--name", "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64",
    "--query", "Parameter.Value"
)

$userData = @"
#!/bin/bash
set -euo pipefail

REGION="$Region"
PARAMETER_PREFIX="$parameterPrefix"
IMAGE_URI="$imageUri"
APP_DOMAIN="$domain"
ECR_REGISTRY="$($repositoryUri.Split("/")[0])"

dnf update -y
dnf install -y docker awscli
systemctl enable --now docker
usermod -aG docker ec2-user || true

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
else
  swapon /swapfile || true
fi

mkdir -p /opt/saccos-lms/caddy_data /opt/saccos-lms/caddy_config
cd /opt/saccos-lms

aws ecr get-login-password --region "`$REGION" | docker login --username AWS --password-stdin "`$ECR_REGISTRY"

tmp_env="`$(mktemp)"
aws ssm get-parameters-by-path \
  --region "`$REGION" \
  --path "`$PARAMETER_PREFIX" \
  --with-decryption \
  --recursive \
  --query 'Parameters[*].[Name,Value]' \
  --output text > "`$tmp_env"

: > .env
while IFS=`$'\t' read -r name value; do
  key="`$(basename "`$name")"
  escaped="`$(printf "%s" "`$value" | sed "s/'/'\"'\"'/g")"
  printf "%s='%s'\n" "`$key" "`$escaped" >> .env
done < "`$tmp_env"
rm -f "`$tmp_env"
chmod 600 .env

cat > Caddyfile <<CADDY
{
  email davidginono625@gmail.com
}

`$APP_DOMAIN {
  encode zstd gzip

  handle /mailpit* {
    reverse_proxy mailpit:8025
  }

  handle {
    reverse_proxy app:8080
  }
}
CADDY

cat > docker-compose.yml <<'COMPOSE'
services:
  app:
    image: $imageUri
    restart: unless-stopped
    mem_limit: 700m
    depends_on:
      - mailpit
    env_file:
      - .env
    expose:
      - "8080"

  mailpit:
    image: axllent/mailpit:latest
    restart: unless-stopped
    env_file:
      - .env
    environment:
      MP_WEBROOT: /mailpit
      MP_UI_AUTH: `"`${SPRING_MAIL_USERNAME}:`${SPRING_MAIL_PASSWORD}`"
      MP_SMTP_AUTH: `"`${SPRING_MAIL_USERNAME}:`${SPRING_MAIL_PASSWORD}`"
      MP_SMTP_AUTH_ALLOW_INSECURE: "true"
    expose:
      - "1025"
      - "8025"

  caddy:
    image: caddy:2-alpine
    restart: unless-stopped
    depends_on:
      - app
      - mailpit
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy_data:/data
      - ./caddy_config:/config
COMPOSE

docker compose version >/dev/null 2>&1 || {
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64" -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
}

docker compose pull
docker compose up -d
"@

$userDataFile = New-TemporaryFile
Set-Content -LiteralPath $userDataFile -Value $userData -NoNewline

$instances = Invoke-AwsJson @(
    "ec2", "describe-instances",
    "--region", $Region,
    "--filters", "Name=tag:Name,Values=$stackSlug-app", "Name=instance-state-name,Values=pending,running,stopping,stopped"
)
$instance = $instances.Reservations | ForEach-Object { $_.Instances } | Select-Object -First 1

if (-not $instance) {
    $subnetId = $subnetIds[0]
    $run = Invoke-AwsJson @(
        "ec2", "run-instances",
        "--region", $Region,
        "--image-id", $amiId,
        "--instance-type", $InstanceType,
        "--subnet-id", $subnetId,
        "--security-group-ids", $webSgId,
        "--iam-instance-profile", "Name=$profileName",
        "--user-data", "file://$userDataFile",
        "--block-device-mappings", "DeviceName=/dev/xvda,Ebs={VolumeSize=12,VolumeType=gp3,DeleteOnTermination=true}",
        "--tag-specifications", "ResourceType=instance,Tags=[{Key=Name,Value=$stackSlug-app},{Key=App,Value=$AppName},{Key=Environment,Value=$Environment}]"
    )
    $instanceId = $run.Instances[0].InstanceId
} else {
    $instanceId = $instance.InstanceId
    if ($instance.State.Name -eq "stopped") {
        & aws ec2 start-instances --region $Region --instance-ids $instanceId *> $null
    }
}
Remove-Item -LiteralPath $userDataFile -Force

Write-Host "Waiting for EC2 instance $instanceId"
& aws ec2 wait instance-running --region $Region --instance-ids $instanceId
if ($LASTEXITCODE -ne 0) {
    throw "EC2 instance did not become running"
}

$addressState = Invoke-AwsJson @("ec2", "describe-addresses", "--region", $Region, "--allocation-ids", $allocationId)
if ($addressState.Addresses[0].InstanceId -ne $instanceId) {
    & aws ec2 associate-address --region $Region --instance-id $instanceId --allocation-id $allocationId --allow-reassociation *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to associate Elastic IP"
    }
}

$ecrRegistry = $repositoryUri.Split("/")[0]
$remoteUpdate = @"
set -euo pipefail

REGION="$Region"
PARAMETER_PREFIX="$parameterPrefix"
IMAGE_URI="$imageUri"
APP_DOMAIN="$domain"
ECR_REGISTRY="$ecrRegistry"

if ! command -v docker >/dev/null 2>&1; then
  dnf install -y docker awscli
  systemctl enable --now docker
fi

systemctl start docker
mkdir -p /opt/saccos-lms/caddy_data /opt/saccos-lms/caddy_config
cd /opt/saccos-lms

aws ecr get-login-password --region "`$REGION" | docker login --username AWS --password-stdin "`$ECR_REGISTRY"

tmp_env="`$(mktemp)"
aws ssm get-parameters-by-path \
  --region "`$REGION" \
  --path "`$PARAMETER_PREFIX" \
  --with-decryption \
  --recursive \
  --query 'Parameters[*].[Name,Value]' \
  --output text > "`$tmp_env"

: > .env
while IFS=`$'\t' read -r name value; do
  key="`$(basename "`$name")"
  escaped="`$(printf "%s" "`$value" | sed "s/'/'\"'\"'/g")"
  printf "%s='%s'\n" "`$key" "`$escaped" >> .env
done < "`$tmp_env"
rm -f "`$tmp_env"
chmod 600 .env

cat > Caddyfile <<CADDY
{
  email davidginono625@gmail.com
}

`$APP_DOMAIN {
  encode zstd gzip

  handle /mailpit* {
    reverse_proxy mailpit:8025
  }

  handle {
    reverse_proxy app:8080
  }
}
CADDY

cat > docker-compose.yml <<'COMPOSE'
services:
  app:
    image: $imageUri
    restart: unless-stopped
    mem_limit: 700m
    depends_on:
      - mailpit
    env_file:
      - .env
    expose:
      - "8080"

  mailpit:
    image: axllent/mailpit:latest
    restart: unless-stopped
    env_file:
      - .env
    environment:
      MP_WEBROOT: /mailpit
      MP_UI_AUTH: `"`${SPRING_MAIL_USERNAME}:`${SPRING_MAIL_PASSWORD}`"
      MP_SMTP_AUTH: `"`${SPRING_MAIL_USERNAME}:`${SPRING_MAIL_PASSWORD}`"
      MP_SMTP_AUTH_ALLOW_INSECURE: "true"
    expose:
      - "1025"
      - "8025"

  caddy:
    image: caddy:2-alpine
    restart: unless-stopped
    depends_on:
      - app
      - mailpit
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./caddy_data:/data
      - ./caddy_config:/config
COMPOSE

docker compose version >/dev/null 2>&1 || {
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64" -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
}

docker compose pull
docker compose up -d --remove-orphans
"@

$ssmPayload = @{
    DocumentName = "AWS-RunShellScript"
    InstanceIds = @($instanceId)
    Comment = "Deploy $imageUri"
    TimeoutSeconds = 900
    Parameters = @{
        commands = @($remoteUpdate)
    }
} | ConvertTo-Json -Depth 8
$ssmPayloadFile = New-TemporaryFile
Set-Content -LiteralPath $ssmPayloadFile -Value $ssmPayload -NoNewline
try {
    $sendCommand = Invoke-AwsJson @("ssm", "send-command", "--region", $Region, "--cli-input-json", "file://$ssmPayloadFile")
} finally {
    Remove-Item -LiteralPath $ssmPayloadFile -Force
}
$commandId = $sendCommand.Command.CommandId
Write-Host "Updating EC2 runtime via SSM command $commandId"
& aws ssm wait command-executed --region $Region --command-id $commandId --instance-id $instanceId
if ($LASTEXITCODE -ne 0) {
    $invocation = Invoke-AwsJson @("ssm", "get-command-invocation", "--region", $Region, "--command-id", $commandId, "--instance-id", $instanceId)
    Write-Host $invocation.StandardOutputContent
    Write-Host $invocation.StandardErrorContent
    throw "EC2 runtime update did not complete successfully"
}
$invocation = Invoke-AwsJson @("ssm", "get-command-invocation", "--region", $Region, "--command-id", $commandId, "--instance-id", $instanceId)
if ($invocation.Status -ne "Success") {
    Write-Host $invocation.StandardOutputContent
    Write-Host $invocation.StandardErrorContent
    throw "EC2 runtime update failed with status $($invocation.Status)"
}

$snapshotId = "$dbIdentifier-initial-$(Get-Date -Format 'yyyyMMddHHmmss')"
$existingSnapshots = Invoke-AwsJson @("rds", "describe-db-snapshots", "--region", $Region, "--db-instance-identifier", $dbIdentifier, "--snapshot-type", "manual")
if ($existingSnapshots.DBSnapshots.Count -eq 0) {
    Write-Host "Creating initial manual DB snapshot $snapshotId"
    & aws rds create-db-snapshot --region $Region --db-instance-identifier $dbIdentifier --db-snapshot-identifier $snapshotId --tags "Key=App,Value=$AppName" "Key=Environment,Value=$Environment" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create initial manual DB snapshot"
    }
} else {
    $snapshotId = $existingSnapshots.DBSnapshots[0].DBSnapshotIdentifier
}

Write-Host "Deployment created."
Write-Host "Endpoint: $baseUrl"
Write-Host "InstanceId: $instanceId"
Write-Host "RDS: $dbIdentifier ($dbEndpoint)"
Write-Host "ManualSnapshot: $snapshotId"
