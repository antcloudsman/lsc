<#
.SYNOPSIS
  Pre-commit guard for the shared worktree: refuse to commit on someone else's branch
  or with build artifacts / foreign paths staged.

.DESCRIPTION
  Prints the staged paths and fails when:
    - HEAD is not on -Branch (strong check: the shared worktree is often switched by others), or
    - a staged path is a build artifact, runtime data, APK, log or tool cache (always denied), or
    - -OwnedPaths is given and a staged path does not match any owned pattern, or
    - -CommitMessage is given and its last line does not end with "[MemberName]".

  This is intentionally NOT installed as a git hook: the shared worktree is often dirty
  during integration, and a hook would block everyone. Run it manually before each commit:

    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-staged-paths.ps1 -Branch feature/lc-server-smoke-gates
    powershell -File scripts\check-staged-paths.ps1 -Branch feature/lc-server-smoke-gates `
        -OwnedPaths 'scripts/*','backend/*','.gitignore'
    powershell -File scripts\check-staged-paths.ps1 -Branch feature/lc-server-smoke-gates `
        -OwnedPaths 'app/src/main.rs' -CommitMessage 'feat(client): ... [LCClient]'

.PARAMETER Branch
  Required. The branch this commit is supposed to land on. If HEAD differs, the check fails.

.PARAMETER OwnedPaths
  Globs of paths the caller owns. Any staged path outside them fails the check.

.PARAMETER CommitMessage
  Optional message to validate; it must end with a "[MemberName]" tag.

.PARAMETER RepoRoot
  Repository root. Default: parent of this script's directory.

.OUTPUTS
  Exit code 0 = clean, 1 = violation or git error.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Branch,
    [string[]]$OwnedPaths,
    [string]$CommitMessage,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)

$failures = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$Message) {
    $script:failures.Add($Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}
function Add-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}
function Write-Info([string]$Message) {
    Write-Host "[ -- ] $Message" -ForegroundColor DarkGray
}

$gitCmd = Get-Command git -ErrorAction SilentlyContinue
if (-not $gitCmd) {
    Add-Failure 'git not found on PATH'
    Write-Host 'STAGED-PATHS: FAIL (1)'
    exit 1
}
if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git'))) {
    Add-Failure "not a git repository: $RepoRoot"
    Write-Host 'STAGED-PATHS: FAIL (1)'
    exit 1
}
$git = $gitCmd.Source

# Strong branch check: in the shared worktree another member may have switched HEAD.
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $headText = (& $git -C $RepoRoot branch --show-current 2>&1 | ForEach-Object { $_.ToString() }) -join "`n"
    $headCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
}
$headBranch = $headText.Trim()
if ($headCode -ne 0 -or -not $headBranch) {
    Add-Failure "could not read current branch in $RepoRoot (detached HEAD?)"
} elseif ($headBranch -ne $Branch) {
    Add-Failure "HEAD is '$headBranch', expected '$Branch' - refusing to commit on another member's branch"
} else {
    Add-Pass "HEAD is on the expected branch: $Branch"
}

# Native stderr under ErrorActionPreference=Stop aborts the script on PS 5.1.
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $stagedText = @(& $git -C $RepoRoot -c core.quotepath=false diff --cached --name-only --diff-filter=ACMRD 2>&1 | ForEach-Object { $_.ToString() })
    $gitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
}
if ($gitCode -ne 0) {
    Add-Failure "git diff --cached failed in $RepoRoot"
    Write-Host 'STAGED-PATHS: FAIL (1)'
    exit 1
}

$staged = @($stagedText | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
if ($staged.Count -eq 0) {
    Write-Info "repo: $RepoRoot"
    Write-Info 'no staged changes'
    if ($failures.Count -eq 0) {
        Write-Host 'STAGED-PATHS: PASS (no staged changes)' -ForegroundColor Green
        exit 0
    }
    # A branch-check failure must never be masked as PASS just because nothing is staged.
    Write-Host "STAGED-PATHS: FAIL ($($failures.Count) violation(s), no staged changes)" -ForegroundColor Red
    exit 1
}

Write-Info "repo: $RepoRoot"
Write-Info "staged $($staged.Count) path(s):"
$show = $staged
if ($show.Count -gt 30) { $show = $show[0..29] }
foreach ($item in $show) { Write-Host "       $item" }
if ($staged.Count -gt $show.Count) { Write-Host "       ... ($($staged.Count - $show.Count) more)" }

$denyPatterns = @(
    'checks/*',
    'capture/*',
    'pinned/*',
    'attachments/*',
    '.inspect/*',
    'app/target/*',
    '*/target/*',
    'app/android/.gradle/*',
    'app/android/build/*',
    'app/android/app/build/*',
    'app/android/.idea/*',
    'app/shots/*',
    'docs/screenshots/*',
    'backend/data/*',
    'backend/node_modules/*',
    '*/node_modules/*',
    '__pycache__/*',
    '*/__pycache__/*',
    '*.apk',
    '*.log',
    '*.jks',
    '*.keystore',
    '*.pyc',
    '*.orig',
    '*.rej',
    '*.tmp',
    'feed.json',
    'craft.json',
    'build-activity.log',
    '.DS_Store',
    'Thumbs.db'
)

$denied = New-Object System.Collections.Generic.List[string]
foreach ($path in $staged) {
    foreach ($pattern in $denyPatterns) {
        if ($path -like $pattern) {
            $denied.Add($path)
            break
        }
    }
}
if ($denied.Count -gt 0) {
    foreach ($path in $denied) { Add-Failure "denied path staged: $path" }
} else {
    Add-Pass 'no build artifact / runtime data staged'
}

if ($OwnedPaths -and $OwnedPaths.Count -gt 0) {
    # powershell -File binds "a/*,b/*" as one string (no array splitting), so accept
    # comma-separated patterns as well as a real PowerShell array.
    $owned = @($OwnedPaths |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim().Trim("'").Trim('"') } |
        Where-Object { $_ } |
        ForEach-Object { $_.Replace('\', '/') })
    $unowned = New-Object System.Collections.Generic.List[string]
    foreach ($path in $staged) {
        $matched = $false
        foreach ($pattern in $owned) {
            if ($path -like $pattern) { $matched = $true; break }
        }
        if (-not $matched) { $unowned.Add($path) }
    }
    if ($unowned.Count -gt 0) {
        foreach ($path in $unowned) { Add-Failure "staged path is not in -OwnedPaths ($($owned -join ', ')): $path" }
    } else {
        Add-Pass "all staged paths match -OwnedPaths ($($owned -join ', '))"
    }
} else {
    Write-Info 'ownership check skipped (no -OwnedPaths given)'
}

if ($CommitMessage) {
    if ($CommitMessage -match '\[[^\[\]]+\]\s*$') {
        Add-Pass 'commit message ends with a [MemberName] tag'
    } else {
        Add-Failure 'commit message does not end with a [MemberName] tag'
    }
}

Write-Host ''
if ($failures.Count -eq 0) {
    Write-Host "STAGED-PATHS: PASS ($($staged.Count) staged)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "STAGED-PATHS: FAIL ($($failures.Count) violation(s), $($staged.Count) staged)" -ForegroundColor Red
    exit 1
}
