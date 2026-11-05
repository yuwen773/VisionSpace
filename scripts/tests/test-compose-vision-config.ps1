$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$env:MYSQL_ROOT_PASSWORD = 'fixture-root'
$env:MYSQL_DATABASE = 'fixture'
$env:SPRING_DATASOURCE_PASSWORD = 'fixture-db'
$env:REDIS_PASSWORD = 'fixture-redis'
$env:SPRING_AI_DASHSCOPE_API_KEY = 'fixture-dashscope'
$env:SPRING_AI_OPENAI_API_KEY = 'fixture-openai'
$env:ALI_YUN_AI_API_KEY = 'fixture-aliyun'
$env:VISION_SPACE_PEXELS_API_KEY = 'fixture-pexels'
$env:VISION_SPACE_VISION_RECOGNITION_API_KEY = 'fixture-vision'
$env:VISION_SPACE_DEFAULT_PASSWORD = 'fixture-default'
$env:VISION_SPACE_VISION_RECOGNITION_BASE_URL = 'http://vision-fixture.invalid:8080'
$env:VISION_SPACE_VISION_RECOGNITION_MODEL = 'fixture-model'

$rendered = docker compose --env-file (Join-Path $repo '.env.example') -f (Join-Path $repo 'docker-compose.yml') config --format json | Out-String
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration could not be rendered' }
$backend = ($rendered | ConvertFrom-Json).services.backend.environment
if ($backend.VISION_SPACE_VISION_RECOGNITION_BASE_URL -ne $env:VISION_SPACE_VISION_RECOGNITION_BASE_URL) {
    throw 'Compose did not forward the configured vision service URL'
}
if ($backend.VISION_SPACE_VISION_RECOGNITION_MODEL -ne $env:VISION_SPACE_VISION_RECOGNITION_MODEL) {
    throw 'Compose did not forward the configured vision model'
}
Remove-Item Env:VISION_SPACE_VISION_RECOGNITION_BASE_URL
Remove-Item Env:VISION_SPACE_VISION_RECOGNITION_MODEL
$rendered = docker compose --env-file (Join-Path $repo '.env.example') -f (Join-Path $repo 'docker-compose.yml') config --format json | Out-String
if ($LASTEXITCODE -ne 0) { throw 'Compose default configuration could not be rendered' }
$backend = ($rendered | ConvertFrom-Json).services.backend.environment
if ($backend.VISION_SPACE_VISION_RECOGNITION_BASE_URL -ne 'https://www.dmxapi.cn' -or
    $backend.VISION_SPACE_VISION_RECOGNITION_MODEL -ne 'GLM-4.1V-Thinking-Flash') {
    throw 'Compose did not preserve the vision service defaults'
}
Write-Host 'PASS: Compose forwards the configured vision service and model'
