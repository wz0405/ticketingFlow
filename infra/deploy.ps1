# TicketingFlow NAS 배포 스크립트 (Windows PowerShell)
# 빌드는 로컬에서, NAS에서는 JAR 담은 얇은 이미지 빌드 + compose 기동만 수행한다.
# 사용법: .\deploy.ps1            전체(빌드+배포)
#         .\deploy.ps1 -SkipBuild 산출물 재사용
#         .\deploy.ps1 -ApiOnly   FE 빌드 생략
param(
    [switch]$SkipBuild,
    [switch]$ApiOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $root "backend"

# deploy.config 로드
$cfg = @{}
Get-Content (Join-Path $PSScriptRoot "deploy.config") | ForEach-Object {
    if ($_ -match "^\s*([A-Z_]+)=(.*)$") { $cfg[$Matches[1]] = $Matches[2].Trim() }
}
$dest = "$($cfg.NAS_USER)@$($cfg.NAS_HOST)"
$dir = $cfg.NAS_DIR

function Invoke-Nas([string]$cmd) {
    plink -batch -ssh -P $cfg.NAS_PORT -pw $cfg.NAS_PASS $dest $cmd
    if ($LASTEXITCODE -ne 0) { throw "ssh failed: $cmd" }
}

function Copy-Nas([string]$src, [string]$remote) {
    pscp -batch -P $cfg.NAS_PORT -pw $cfg.NAS_PASS -r $src "${dest}:$remote"
    if ($LASTEXITCODE -ne 0) { throw "scp failed: $src" }
}

if (-not $SkipBuild) {
    Write-Host "[1/5] backend build"
    Push-Location $backend
    ./gradlew bootJar -x test --console=plain
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "gradle build failed" }
    Pop-Location

    if (-not $ApiOnly) {
        Write-Host "[1/5] frontend build"
        Push-Location $cfg.WEB_DIR
        flutter build web --release
        if ($LASTEXITCODE -ne 0) { Pop-Location; throw "flutter build failed" }
        Pop-Location
    }
}

Write-Host "[2/5] prepare remote dirs"
Invoke-Nas "mkdir -p $dir/jars $dir/nginx $dir/web"

Write-Host "[3/5] upload artifacts"
Copy-Nas "$backend\queue-api\build\libs\queue-api-0.1.0.jar"   "$dir/jars/"
Copy-Nas "$backend\booking-api\build\libs\booking-api-0.1.0.jar" "$dir/jars/"
Copy-Nas "$backend\worker\build\libs\worker-0.1.0.jar"          "$dir/jars/"
Copy-Nas "$root\infra\docker\Dockerfile.app"                 "$dir/"
Copy-Nas "$root\infra\docker-compose.yml"                    "$dir/"
Copy-Nas "$root\infra\nginx\nginx.conf"                      "$dir/nginx/"
Copy-Nas "$root\infra\nginx\routes.inc"                      "$dir/nginx/"
if (-not $ApiOnly) {
    Copy-Nas "$($cfg.WEB_DIR)\build\web\*" "$dir/web/"
}

Write-Host "[4/5] build images on NAS"
$sudo = "echo '$($cfg.NAS_PASS)' | sudo -S"
Invoke-Nas "$sudo sh -c 'cd $dir && /usr/local/bin/docker build -f Dockerfile.app --build-arg JAR_FILE=jars/queue-api-0.1.0.jar -t ticketingflow/queue-api:latest . && /usr/local/bin/docker build -f Dockerfile.app --build-arg JAR_FILE=jars/booking-api-0.1.0.jar -t ticketingflow/booking-api:latest . && /usr/local/bin/docker build -f Dockerfile.app --build-arg JAR_FILE=jars/worker-0.1.0.jar -t ticketingflow/worker:latest .' 2>&1 | grep -v -i password"

Write-Host "[5/5] compose up"
Invoke-Nas "$sudo sh -c 'cd $dir && /usr/local/bin/docker-compose up -d --remove-orphans && /usr/local/bin/docker ps' 2>&1 | grep -v -i password"

# 컨테이너 재생성으로 내부 IP가 바뀌면 nginx의 upstream DNS 캐시가 낡아 502가 난다 — 리로드로 재해석
Write-Host "[6/6] nginx upstream re-resolve"
Invoke-Nas "{ $sudo /usr/local/bin/docker exec tr-nginx nginx -s reload; } 2>&1 | grep -v -i password || true"

Write-Host "deploy done: http://$($cfg.NAS_HOST):8080"
