# CommandControls Build Script
# Usage: .\build.ps1 [option]
# Options:
#   1 or build     - Build without version change
#   2 or patch     - Build with patch increment (1.0.0 -> 1.0.1)
#   3 or minor     - Build with minor/service pack increment (1.0.0 -> 1.1.0)
#   4 or major     - Build with major/feature release increment (1.0.0 -> 2.0.0)
#   5 or rollback  - Rollback version (undo last patch/minor/major) and rebuild

param(
    [Parameter(Position=0)]
    [string]$Option,
    
    [Parameter()]
    [switch]$Force
)

$PropertiesFile = "gradle.properties"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ScriptDir "build\libs"

# Colors for output
function Write-ColorText {
    param([string]$text, [string]$color)
    Write-Host $text -ForegroundColor $color
}

# Read current version from gradle.properties
function Get-CurrentVersion {
    $content = Get-Content $PropertiesFile
    foreach ($line in $content) {
        if ($line -match "^mod_version=(.+)$") {
            return $matches[1]
        }
    }
    return "1.0.0"
}

# Read minecraft version
function Get-MinecraftVersion {
    $content = Get-Content $PropertiesFile
    foreach ($line in $content) {
        if ($line -match "^minecraft_version=(.+)$") {
            return $matches[1]
        }
    }
    return "1.20.1"
}

# Update version in gradle.properties
function Set-Version {
    param([string]$newVersion)
    $content = Get-Content $PropertiesFile
    $newContent = $content -replace "^mod_version=.+$", "mod_version=$newVersion"
    Set-Content $PropertiesFile $newContent
}

# Increment version
function Get-NewVersion {
    param([string]$current, [string]$type)
    $parts = $current.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]
    
    switch ($type) {
        "patch" { $patch++ }
        "minor" { $minor++; $patch = 0 }
        "major" { $major++; $minor = 0; $patch = 0 }
    }
    
    return "$major.$minor.$patch"
}

# Decrement version (rollback)
function Get-PreviousVersion {
    param([string]$current)
    $parts = $current.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]
    
    # Try to decrement patch first
    if ($patch -gt 0) {
        $patch--
        return "$major.$minor.$patch"
    }
    # If patch is 0, try to decrement minor
    elseif ($minor -gt 0) {
        $minor--
        $patch = 9  # Assume previous patch was 9 (or you could prompt)
        return "$major.$minor.$patch"
    }
    # If minor is 0, try to decrement major
    elseif ($major -gt 0) {
        $major--
        $minor = 9  # Assume previous minor was 9
        $patch = 9
        return "$major.$minor.$patch"
    }
    # Can't go lower than 0.0.0
    return "0.0.0"
}

# Run gradle build
function Invoke-Build {
    Write-ColorText "`n[BUILD] Building..." "Cyan"
    & .\gradlew build
    
    if ($LASTEXITCODE -eq 0) {
        $mcVersion = Get-MinecraftVersion
        $modVersion = Get-CurrentVersion
        
        Write-ColorText "`n[OK] Build successful!" "Green"
        Write-ColorText "[JAR] CommandControls-$modVersion-$mcVersion.jar" "Yellow"
        
        # Rename the jar to include MC version
        $sourceJar = Get-ChildItem "$BuildDir\commandcontrols-*.jar" -Exclude "*-sources.jar" | Select-Object -First 1
        if ($sourceJar) {
            $targetName = "CommandControls-$modVersion-$mcVersion.jar"
            $targetPath = "$BuildDir\$targetName"
            
            # Remove old file if exists
            if (Test-Path $targetPath) {
                Remove-Item $targetPath
            }
            
            Copy-Item $sourceJar.FullName $targetPath
            Write-ColorText "[COPY] $targetPath" "Gray"
        }
    } else {
        Write-ColorText "`n[ERROR] Build failed!" "Red"
        exit 1
    }
}

# Show menu if no option provided
function Show-Menu {
    $currentVersion = Get-CurrentVersion
    $mcVersion = Get-MinecraftVersion
    
    Write-ColorText "`n========================================" "Cyan"
    Write-ColorText "      CommandControls Build System      " "Cyan"
    Write-ColorText "========================================" "Cyan"
    Write-ColorText "`nCurrent Version: $currentVersion (MC $mcVersion)" "Yellow"
    Write-ColorText "`nSelect build option:" "White"
    Write-ColorText "  [1] Build only (no version change)" "White"
    Write-ColorText "  [2] Patch   ($currentVersion -> $(Get-NewVersion $currentVersion 'patch'))" "White"
    Write-ColorText "  [3] Minor   ($currentVersion -> $(Get-NewVersion $currentVersion 'minor'))" "White"
    Write-ColorText "  [4] Major   ($currentVersion -> $(Get-NewVersion $currentVersion 'major'))" "White"
    Write-ColorText "  [5] Rollback ($currentVersion -> $(Get-PreviousVersion $currentVersion))" "Magenta"
    Write-ColorText "  [Q] Quit" "Gray"
    Write-Host ""
    
    $choice = Read-Host "Enter option"
    return $choice
}

# Main logic
$currentVersion = Get-CurrentVersion

if (-not $Option) {
    $Option = Show-Menu
}

switch ($Option.ToLower()) {
    { $_ -in "1", "build" } {
        Write-ColorText "`n[BUILD] Building version $currentVersion (no change)" "Yellow"
        Invoke-Build
    }
    { $_ -in "2", "patch" } {
        $newVersion = Get-NewVersion $currentVersion "patch"
        Write-ColorText "`n[PATCH] $currentVersion -> $newVersion" "Yellow"
        Set-Version $newVersion
        Invoke-Build
    }
    { $_ -in "3", "minor", "service" } {
        $newVersion = Get-NewVersion $currentVersion "minor"
        Write-ColorText "`n[MINOR] $currentVersion -> $newVersion" "Yellow"
        Set-Version $newVersion
        Invoke-Build
    }
    { $_ -in "4", "major", "feature" } {
        $newVersion = Get-NewVersion $currentVersion "major"
        Write-ColorText "`n[MAJOR] $currentVersion -> $newVersion" "Yellow"
        Set-Version $newVersion
        Invoke-Build
    }
    { $_ -in "5", "rollback", "undo" } {
        $prevVersion = Get-PreviousVersion $currentVersion
        if ($prevVersion -eq $currentVersion) {
            Write-ColorText "`n[ERROR] Cannot rollback from version $currentVersion" "Red"
            exit 1
        }
        Write-ColorText "`n[ROLLBACK] $currentVersion -> $prevVersion" "Magenta"
        
        if ($Force) {
            Set-Version $prevVersion
            Invoke-Build
        } else {
            $confirm = Read-Host "Are you sure? (y/N)"
            if ($confirm.ToLower() -eq "y") {
                Set-Version $prevVersion
                Invoke-Build
            } else {
                Write-ColorText "Rollback cancelled." "Gray"
            }
        }
    }
    { $_ -in "q", "quit", "exit" } {
        Write-ColorText "Bye!" "Gray"
        exit 0
    }
    default {
        Write-ColorText "[ERROR] Invalid option: $Option" "Red"
        Write-ColorText "Usage: .\build.ps1 [1|2|3|4|5|build|patch|minor|major|rollback]" "Gray"
        exit 1
    }
}

Write-Host ""
