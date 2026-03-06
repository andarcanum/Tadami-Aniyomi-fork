param(
    [string]$InputPath = "tools/ci/aurora-contrast-samples.csv",
    [double]$MinContrast = 4.5,
    [switch]$Strict
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-HexToRgb {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Hex
    )

    $normalized = $Hex.Trim().TrimStart("#")
    if ($normalized.Length -eq 3) {
        $normalized = ($normalized.ToCharArray() | ForEach-Object { "$_$_" }) -join ""
    }
    if ($normalized.Length -eq 8) {
        $normalized = $normalized.Substring(2, 6)
    }
    if ($normalized.Length -ne 6 -or $normalized -notmatch "^[0-9A-Fa-f]{6}$") {
        throw "Invalid hex color '$Hex'. Expected RGB (RRGGBB) or ARGB (AARRGGBB)."
    }

    return @{
        R = [Convert]::ToInt32($normalized.Substring(0, 2), 16) / 255.0
        G = [Convert]::ToInt32($normalized.Substring(2, 2), 16) / 255.0
        B = [Convert]::ToInt32($normalized.Substring(4, 2), 16) / 255.0
    }
}

function Convert-SrgbChannelToLinear {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    if ($Value -le 0.04045) {
        return $Value / 12.92
    }
    return [Math]::Pow(($Value + 0.055) / 1.055, 2.4)
}

function Get-RelativeLuminance {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Rgb
    )

    $r = Convert-SrgbChannelToLinear -Value $Rgb.R
    $g = Convert-SrgbChannelToLinear -Value $Rgb.G
    $b = Convert-SrgbChannelToLinear -Value $Rgb.B
    return (0.2126 * $r) + (0.7152 * $g) + (0.0722 * $b)
}

function Get-ContrastRatio {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ForegroundHex,
        [Parameter(Mandatory = $true)]
        [string]$BackgroundHex
    )

    $fgLum = Get-RelativeLuminance -Rgb (Convert-HexToRgb -Hex $ForegroundHex)
    $bgLum = Get-RelativeLuminance -Rgb (Convert-HexToRgb -Hex $BackgroundHex)
    $lighter = [Math]::Max($fgLum, $bgLum)
    $darker = [Math]::Min($fgLum, $bgLum)
    return ($lighter + 0.05) / ($darker + 0.05)
}

function Get-RowValue {
    param(
        [Parameter(Mandatory = $true)]
        [psobject]$Row,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $prop = $Row.PSObject.Properties[$Name]
    if ($null -eq $prop) {
        return $null
    }
    return [string]$prop.Value
}

if (-not (Test-Path -Path $InputPath)) {
    if ($Strict) {
        throw "Contrast samples not found at '$InputPath'."
    }
    Write-Warning "Contrast samples not found at '$InputPath'. Skipping check."
    exit 0
}

$rows = @(Import-Csv -Path $InputPath)
$rowCount = @($rows).Count
if ($rowCount -eq 0) {
    if ($Strict) {
        throw "No rows found in '$InputPath'."
    }
    Write-Warning "No rows found in '$InputPath'. Skipping check."
    exit 0
}

$failures = @()
$processedCount = 0
$skippedCount = 0
foreach ($row in $rows) {
    $foregroundHex = Get-RowValue -Row $row -Name "foreground_hex"
    $backgroundHex = Get-RowValue -Row $row -Name "background_hex"
    $scenario = Get-RowValue -Row $row -Name "scenario"
    $id = Get-RowValue -Row $row -Name "id"
    if (-not $scenario) {
        $scenario = "unknown-scenario"
    }
    if (-not $id) {
        $id = "row-$scenario"
    }

    if (-not $foregroundHex -or -not $backgroundHex) {
        $message = "Row '$id' is missing required fields: foreground_hex/background_hex."
        if ($Strict) {
            throw $message
        }
        Write-Warning $message
        $skippedCount += 1
        continue
    }

    $ratio = Get-ContrastRatio -ForegroundHex $foregroundHex -BackgroundHex $backgroundHex
    $processedCount += 1
    Write-Host ("[contrast] id={0} scenario={1} ratio={2:N2}" -f $id, $scenario, $ratio)

    if ($ratio -lt $MinContrast) {
        $failures += [PSCustomObject]@{
            id             = $id
            scenario       = $scenario
            foreground_hex = $foregroundHex
            background_hex = $backgroundHex
            ratio          = [Math]::Round($ratio, 2)
            required       = $MinContrast
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Contrast failures:"
    $failures | Format-Table -AutoSize | Out-String | Write-Host
    if ($Strict) {
        throw "Contrast validation failed for $($failures.Count) sample(s)."
    }
    Write-Warning "Contrast validation has failures, but strict mode is OFF."
    exit 0
}

if ($processedCount -eq 0) {
    if ($Strict) {
        throw "No valid contrast samples were processed from '$InputPath'."
    }
    Write-Warning "No valid contrast samples were processed from '$InputPath'."
    exit 0
}

$message = "Contrast validation passed for $processedCount processed sample(s)."
if ($skippedCount -gt 0) {
    $message += " Skipped: $skippedCount invalid row(s)."
}
Write-Host $message
