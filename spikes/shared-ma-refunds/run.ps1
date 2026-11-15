param([string[]] $Case = @())

$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $taskRoot
$env:JAVA_HOME = 'C:\Users\Alejandro\.jdks\ms-25.0.3'
$evidence = Join-Path $PSScriptRoot 'build/evidence'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

$cases = @(
    @{ Name='nf-both'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='normal' },
    @{ Name='nf-after'; Loader='NeoForge'; Graph='current'; Order='after'; Variant='normal' },
    @{ Name='nf-boots'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PsharedMaGraph=boots') },
    @{ Name='fabric-alone'; Loader='Fabric'; Graph='alone'; Order='before'; Variant='normal' },
    @{ Name='fabric-both'; Loader='Fabric'; Graph='current'; Order='before'; Variant='normal' },
    @{ Name='fabric-after'; Loader='Fabric'; Graph='current'; Order='after'; Variant='normal' },
    @{ Name='fabric-boots'; Loader='Fabric'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PsharedMaGraph=boots') },
    @{ Name='fabric-core'; Loader='Fabric'; Graph='alone'; Order='before'; Variant='normal'; Extra=@('-PsharedMaGraph=core') },
    @{ Name='nf-flow'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='flow' },
    @{ Name='fabric-flow'; Loader='Fabric'; Graph='current'; Order='before'; Variant='flow' },
    @{ Name='nf-compatible'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='compatible' },
    @{ Name='nf-absent'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PleggingsMaAbsent=true'); Absent=$true },
    @{ Name='fabric-absent'; Loader='Fabric'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PleggingsMaAbsent=true'); Absent=$true },
    @{ Name='nf-joint'; Loader='NeoForge'; Jdt=$true; Graph='both' },
    @{ Name='nf-joint-after'; Loader='NeoForge'; Jdt=$true; Graph='both'; Extra=@('-PsharedMaOrder=after') },
    @{ Name='nf-forward'; Loader='NeoForge'; Jdt=$true; Graph='both'; Fault='forward' },
    @{ Name='nf-core'; Loader='NeoForge'; Jdt=$true; Graph='core'; Fault='jdtAbsent' },
    @{ Name='nf-floor'; Loader='NeoForge'; Jdt=$true; Graph='both'; Floor=$true },
    @{ Name='nf-timing-current'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PsharedMaTiming=true', '-PsharedMaBaseline=true') },
    @{ Name='nf-timing-candidate'; Loader='NeoForge'; Graph='current'; Order='before'; Variant='normal'; Extra=@('-PsharedMaTiming=true') }
)

foreach ($profile in $cases) {
    if ($Case.Count -gt 0 -and $profile.Name -notin $Case) { continue }
    $baseline = if ($profile.Loader -eq 'Fabric') { '0.19.5' } elseif ($profile.Floor) { '26.1.2.99' } else { '26.1.2.114' }
    $label = if ($profile.Jdt) { 'jdt-refunds-' + $profile.Graph + $(if ($profile.Fault) { '-' + $profile.Fault }) }
             else { "mystical-refunds-$($profile.Graph)-$($profile.Order)-$($profile.Variant)" + $(if ($profile.Absent) { '-absent' }) }
    $runtime = Join-Path $taskRoot "mods/MismatchedLeggings/RuntimeTests/$($profile.Loader)/build"
    $report = Join-Path $runtime "reports/$baseline/$label"
    $mods = Join-Path $runtime "run/$baseline/$label/server/mods"
    # Preserve an obsolete generated staging copy, if present; never read indev mods.
    $oldBoots = Join-Path $mods "MismatchedBoots-26.1.2-$($profile.Loader.ToLower())-1.0.0.jar"
    if (Test-Path -LiteralPath $oldBoots) {
        $hash = (Get-FileHash -LiteralPath $oldBoots -Algorithm SHA256).Hash
        $backup = Join-Path $evidence "old-staged-$($profile.Name)-$hash.jar"
        Move-Item -LiteralPath $oldBoots -Destination $backup
    }
    $task = if ($profile.Jdt) { 'runJustDireRefundSeam' } else { 'runMysticalRefundsServer' }
    $arguments = @(':MismatchedLeggings:RuntimeTests:{0}:{1}' -f $profile.Loader, $task)
    $arguments += @(
                   '--init-script', 'spikes/shared-ma-refunds/init.gradle', '--offline', '--console=plain')
    if ($profile.Jdt) {
        $arguments += '-PleggingsRefundGraph=' + $profile.Graph
        if ($profile.Fault) { $arguments += '-PleggingsRefundFault=' + $profile.Fault }
    } else {
        $arguments += @("-PleggingsMaGraph=$($profile.Graph)", "-PleggingsMaOrder=$($profile.Order)",
                        "-PleggingsMaRefundVariant=$($profile.Variant)", "-PsharedMaOrder=$($profile.Order)")
    }
    if ($profile.Floor) { $arguments += '-Pneoforge_version=26.1.2.99' }
    if ($profile.Extra) { $arguments += $profile.Extra }
    $log = Join-Path $evidence ($profile.Name + '.log')
    $started = [DateTime]::UtcNow
    & .\gradlew.bat @arguments *> $log
    $exitCode = $LASTEXITCODE
    if ('-PsharedMaGraph=core' -in $profile.Extra) {
        $coreCopy = Join-Path $mods "mismatchedthings-26.1.2-$($profile.Loader.ToLower())-1.1.0.jar"
        if (Test-Path -LiteralPath $coreCopy) { Remove-Item -LiteralPath $coreCopy }
    }
    $ok = Join-Path $report 'server.ok'
    $failed = Join-Path $report 'server.failure.txt'
    if ($exitCode -ne 0 -or !(Test-Path -LiteralPath $ok) -or (Get-Item -LiteralPath $ok).LastWriteTimeUtc -lt $started -or (Test-Path -LiteralPath $failed)) {
        Get-Content -LiteralPath $log -Tail 22
        if (Test-Path -LiteralPath $failed) { Get-Content -LiteralPath $failed -TotalCount 10 }
        throw "Spike failed: $($profile.Name); see $log"
    }
    if (Select-String -LiteralPath $log -Pattern '/ERROR]|/FATAL]' -Quiet) { throw "Unexpected runtime errors: $log" }
    $destination = Join-Path $evidence $profile.Name
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Copy-Item -LiteralPath $ok -Destination $destination
    foreach ($name in @('refunds.txt', 'boots-refunds.txt')) {
        $source = Join-Path $report $name
        if ((Test-Path -LiteralPath $source) -and (Get-Item -LiteralPath $source).LastWriteTimeUtc -ge $started) {
            Copy-Item -LiteralPath $source -Destination $destination
        }
    }
    Write-Output "PASS $($profile.Name) ($baseline)"
}
