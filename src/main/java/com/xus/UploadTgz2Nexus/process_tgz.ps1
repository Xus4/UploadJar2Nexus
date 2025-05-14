# 生成依赖信息文件
npm ls --json --all > dependencies.json

# 读取 dependencies.json 文件
$dependenciesJson = Get-Content -Path "dependencies.json" -Raw | ConvertFrom-Json

# 创建目录保存打包文件
$outputDir = "packed-dependencies"
if (-Not (Test-Path -Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir
}

# 遍历依赖并打包
foreach ($package in $dependenciesJson.dependencies.PSObject.Properties) {
    $packageName = $package.Name
    $packageVersion = $package.Value.version
    $packageSpec = "$packageName@$packageVersion"

    Write-Host "Packaging $packageSpec..."

    # 使用 npm pack 打包依赖
    $tarball = npm pack $packageSpec --pack-destination $outputDir 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host "Packaged: $tarball"
    } else {
        Write-Host "Failed to package: $packageSpec"
    }
}

Write-Host "All dependencies packaged successfully! Output directory: $outputDir"