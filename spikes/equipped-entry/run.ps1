param([string[]]$Case = @())
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cases = @(
    @{name='fabric-baseline'; loader='Fabric'; graph='both'; mode='baseline'},
    @{name='nf-baseline'; loader='NeoForge'; graph='both'; mode='baseline'},
    @{name='fabric-legs'; loader='Fabric'; graph='legs'},
    @{name='fabric-boots'; loader='Fabric'; graph='boots'},
    @{name='fabric-before'; loader='Fabric'; graph='both'; forward=$true},
    @{name='fabric-after'; loader='Fabric'; graph='both'; order='after'; forward=$true},
    @{name='nf-legs'; loader='NeoForge'; graph='legs'},
    @{name='nf-boots'; loader='NeoForge'; graph='boots'},
    @{name='nf-native'; loader='NeoForge'; graph='both'; native=$true; forward=$true},
    @{name='nf-after'; loader='NeoForge'; graph='both'; order='after'; forward=$true},
    @{name='nf-floor'; loader='NeoForge'; graph='both'; order='after'; forward=$true; baseline='26.1.2.99'}
)
if ($Case.Count -gt 0) {
    $selected = $Case -join ','
    $names = $selected.Split(',')
    if (@($names | Where-Object { $_ -notin $cases.name }).Count -gt 0) { throw 'Unknown spike case' }
    $cases = @($cases | Where-Object { $_.name -in $names })
}
Push-Location $repoRoot
try {
    foreach ($caseData in $cases) {
        $loader = $caseData.loader
        $baseline = if ($caseData.baseline) {$caseData.baseline} elseif ($loader -eq 'Fabric') {'0.19.5'} else {'26.1.2.114'}
        $variant = 'equipped' + $(if ($caseData.graph -ne 'legs') {'-boots'} else {''}) + $(if ($caseData.native) {'-ice-and-fire'} else {''})
        $run = Join-Path $repoRoot "mods/MismatchedLeggings/RuntimeTests/$loader/build/run/$baseline/$variant"
        $reports = Join-Path $repoRoot "mods/MismatchedLeggings/RuntimeTests/$loader/build/reports/$baseline/$variant"
        $evidence = Join-Path $PSScriptRoot "build/evidence/$($caseData.name)"
        New-Item -ItemType Directory -Force -Path $evidence | Out-Null
        Remove-Item -LiteralPath (Join-Path $evidence 'passed.txt') -Force -ErrorAction SilentlyContinue
        $options = @('-I','spikes/equipped-entry/init.gradle',"-PentryLoader=$loader", "-PentryGraph=$($caseData.graph)",
            "-PentryMode=$(if ($caseData.mode) {$caseData.mode} else {'candidate'})",
            "-PentryOrder=$(if ($caseData.order) {$caseData.order} else {'before'})",
            "-PentryForward=$(([bool]$caseData.forward).ToString().ToLowerInvariant())", "-PentryNative=$(([bool]$caseData.native).ToString().ToLowerInvariant())",
            "-PleggingsBoots=$(($caseData.graph -ne 'legs').ToString().ToLowerInvariant())", "-PleggingsIceAndFire=$(([bool]$caseData.native).ToString().ToLowerInvariant())",
            "-Pneoforge_version=$(if ($loader -eq 'NeoForge') {$baseline} else {'26.1.2.114'})", '--offline','--console=plain')
        Write-Output "Entry spike $($caseData.name)"
        try {
            foreach ($phase in @('Server','Client')) {
                # A previously generated 1.0.0 staging copy is not the published input fixture.
                $oldName = "MismatchedBoots-26.1.2-$($loader.ToLowerInvariant())-1.0.0.jar"
                $oldCopy = Join-Path $run "$($phase.ToLowerInvariant())/mods/$oldName"
                if (Test-Path -LiteralPath $oldCopy) { Remove-Item -LiteralPath $oldCopy }
                $log = Join-Path $evidence "$($phase.ToLowerInvariant()).log"
                $tasks = @()
                if ($phase -eq 'Server') { $tasks += @(':MismatchedLeggings:RuntimeTests:' + "${loader}:compileEntrySpike"); $tasks += '--rerun' }
                $tasks += ":MismatchedLeggings:RuntimeTests:${loader}:runEquipped${phase}"
                & ./gradlew.bat @tasks @options *> $log
                if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 28; throw "Failed $($caseData.name)/$phase" }
                if (!(Test-Path -LiteralPath (Join-Path $reports "$($phase.ToLowerInvariant()).ok")) -or
                    (Test-Path -LiteralPath (Join-Path $reports "$($phase.ToLowerInvariant()).failure.txt"))) {
                    if (Test-Path -LiteralPath (Join-Path $reports "$($phase.ToLowerInvariant()).failure.txt")) {
                        Get-Content -LiteralPath (Join-Path $reports "$($phase.ToLowerInvariant()).failure.txt")
                    }
                    throw "Missing/failed $($caseData.name)/$phase marker"
                }
                if (Select-String -LiteralPath $log -Pattern '/ERROR\]|/FATAL\]' -Quiet) { throw "Unexpected runtime error: $log" }
            }
            Copy-Item -LiteralPath (Join-Path $reports 'entry-spike.txt') -Destination $evidence
            Copy-Item -LiteralPath (Join-Path $run 'client/.mixin.out/class/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.class') -Destination $evidence
            Set-Content -LiteralPath (Join-Path $evidence 'passed.txt') -Value 'Actual native armor entry and reload accepted; no production integration'
            Write-Output "PASS $($caseData.name)"
        } finally {
            # Restore exactly the staged artifacts modified/removed by this opt-in route.
            foreach ($phase in @('server','client')) {
                $mods = Join-Path $run "$phase/mods"
                if (!(Test-Path -LiteralPath $mods)) { continue }
                Remove-Item -LiteralPath (Join-Path $mods 'entry-spike.jar') -Force -ErrorAction SilentlyContinue
                if ($caseData.native) { Remove-Item -LiteralPath (Join-Path $mods 'RootsClassic-26.1.2-1.6.7.jar') -Force -ErrorAction SilentlyContinue }
                foreach ($product in @('MismatchedBoots','MismatchedLeggings')) {
                    $normal = Join-Path $repoRoot "mods/$product/$loader/build/libs/$product-26.1.2-$($loader.ToLowerInvariant())-$(if ($product -eq 'MismatchedBoots') {'1.1.0'} else {'0.1.0-dev'}).jar"
                    if (Test-Path -LiteralPath $normal) {
                        if ($product -eq 'MismatchedBoots' -and $caseData.graph -eq 'legs') { continue }
                        Copy-Item -LiteralPath $normal -Destination $mods -Force
                        if ((Get-FileHash -LiteralPath $normal).Hash -ne (Get-FileHash -LiteralPath (Join-Path $mods (Split-Path $normal -Leaf))).Hash) { throw 'Staged artifact restoration failed' }
                    }
                }
            }
        }
    }
} finally { Pop-Location }
