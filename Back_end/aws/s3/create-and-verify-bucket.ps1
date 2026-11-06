[CmdletBinding()]
param (
    [string]$Bucket = "clouddocs-dev-private",
    [string]$Region = "ap-southeast-1",
    [string]$Profile = ""
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw "AWS CLI v2 chưa được cài hoặc chưa có trong PATH."
}

$awsArgs = @()
if ($Profile) {
    $awsArgs += @("--profile", $Profile)
}

& aws @awsArgs sts get-caller-identity --output json | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Không xác thực được AWS credentials/profile."
}

& aws @awsArgs s3api head-bucket --bucket $Bucket 2>$null
$bucketExists = $LASTEXITCODE -eq 0

if (-not $bucketExists) {
    $createArgs = @('s3api', 'create-bucket', '--bucket', $Bucket, '--region', $Region,
        '--object-ownership', 'BucketOwnerEnforced', '--no-cli-pager')
    if ($Region -ne 'us-east-1') {
        $createArgs += @('--create-bucket-configuration', "LocationConstraint=$Region")
    }
    & aws @awsArgs @createArgs | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể tạo bucket. Tên bucket S3 là duy nhất toàn cầu; hãy kiểm tra quyền hoặc chọn tên khác."
    }
}

& aws @awsArgs s3api put-public-access-block `
    --bucket $Bucket `
    --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true" `
    --no-cli-pager | Out-Null
if ($LASTEXITCODE -ne 0) { throw "AWS operation failed: put-public-access-block" }

& aws @awsArgs s3api put-bucket-ownership-controls `
    --bucket $Bucket `
    --ownership-controls "Rules=[{ObjectOwnership=BucketOwnerEnforced}]" `
    --no-cli-pager | Out-Null
if ($LASTEXITCODE -ne 0) { throw "AWS operation failed: put-bucket-ownership-controls" }

& aws @awsArgs s3api put-bucket-encryption `
    --bucket $Bucket `
    --server-side-encryption-configuration "Rules=[{ApplyServerSideEncryptionByDefault={SSEAlgorithm=AES256},BucketKeyEnabled=false}]" `
    --no-cli-pager | Out-Null
if ($LASTEXITCODE -ne 0) { throw "AWS operation failed: put-bucket-encryption" }

$corsFile = Join-Path $PSScriptRoot "cors.json"
& aws @awsArgs s3api put-bucket-cors `
    --bucket $Bucket `
    --cors-configuration "file://$corsFile" `
    --no-cli-pager | Out-Null
if ($LASTEXITCODE -ne 0) { throw "AWS operation failed: put-bucket-cors" }

$location = & aws @awsArgs s3api get-bucket-location --bucket $Bucket --query LocationConstraint --output text
if ($LASTEXITCODE -ne 0) { throw "AWS verification failed: location" }
$publicAccess = & aws @awsArgs s3api get-public-access-block --bucket $Bucket --query PublicAccessBlockConfiguration --output json
if ($LASTEXITCODE -ne 0) { throw "AWS verification failed: publicAccess" }
$ownership = & aws @awsArgs s3api get-bucket-ownership-controls --bucket $Bucket --query "OwnershipControls.Rules[0].ObjectOwnership" --output text
if ($LASTEXITCODE -ne 0) { throw "AWS verification failed: ownership" }
$encryption = & aws @awsArgs s3api get-bucket-encryption --bucket $Bucket --query "ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm" --output text
if ($LASTEXITCODE -ne 0) { throw "AWS verification failed: encryption" }

if ($location -eq 'None' -or [string]::IsNullOrWhiteSpace($location)) { $location = 'us-east-1' }

if ($location -ne $Region) {
    throw "Sai region: $location"
}

if ($ownership -ne "BucketOwnerEnforced") {
    throw "ACL chưa được tắt: $ownership"
}

if ($encryption -ne "AES256") {
    throw "Mã hóa mặc định chưa đúng: $encryption"
}

$publicAccessObject = $publicAccess | ConvertFrom-Json
if (-not (
    $publicAccessObject.BlockPublicAcls -and
    $publicAccessObject.IgnorePublicAcls -and
    $publicAccessObject.BlockPublicPolicy -and
    $publicAccessObject.RestrictPublicBuckets
)) {
    throw "Block Public Access chưa bật đủ bốn tùy chọn."
}

Write-Host "PASS: s3://$Bucket ở $Region"
Write-Host "PASS: private, Block Public Access ON, ACL disabled (BucketOwnerEnforced)"
Write-Host "PASS: default encryption SSE-S3 (AES256), CORS cho local frontend đã cấu hình"
