# gen-mechanics.ps1 - single-source code generator for app/mechanics.json (v2)
#
# Emits the marked GENERATED region inside:
#   app/src/main.rs                                        (Rust tables + ring scalars)
#   app/android/app/src/main/java/com/luopan/fengshui/CompassData.java
#
# app/mechanics.json is the only hand-edited source. Never edit the generated regions:
#   pwsh -File tools/mechanics/gen-mechanics.ps1          # regenerate in place
#   pwsh -File tools/mechanics/gen-mechanics.ps1 -Check   # verify, exit 1 if stale
#
# Script body is pure ASCII on purpose (PowerShell 5.1 reads BOM-less scripts as ANSI).

[CmdletBinding()]
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$Inv = [System.Globalization.CultureInfo]::InvariantCulture
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$JsonPath = Join-Path $Root 'app\mechanics.json'
$RustPath = Join-Path $Root 'app\src\main.rs'
$JavaPath = Join-Path $Root 'app\android\app\src\main\java\com\luopan\fengshui\CompassData.java'

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$Open = '// >>> BEGIN GENERATED (app/mechanics.json) >>>'
$Close = '// <<< END GENERATED <<<'

function Read-Utf8([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, $Utf8NoBom)
}

function Write-Utf8([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $Utf8NoBom)
}

function Fmt-RustFloat([double]$v) {
    if ($v -eq [math]::Floor($v)) { return [string]::Format($Inv, '{0}.0', $v) }
    return $v.ToString('R', $Inv)
}

function Fmt-JavaFloat([double]$v) {
    if ($v -eq [math]::Floor($v)) { return [string]::Format($Inv, '{0}f', $v) }
    return $v.ToString('R', $Inv) + 'f'
}

function Esc-Rust([string]$s) { return $s.Replace('\', '\\').Replace('"', '\"') }

function Esc-Java([string]$s) {
    $s = $s.Replace('\', '\\').Replace('"', '\"')
    return $s.Replace("`r", '').Replace("`n", '\n')
}

function Rust-StrArray([string]$name, $items, [int]$perLine) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("const ${name}: [&str; $($items.Count)] = [`n")
    for ($i = 0; $i -lt $items.Count; $i++) {
        if ($i % $perLine -eq 0) { [void]$sb.Append('    ') }
        [void]$sb.Append('"' + (Esc-Rust $items[$i]) + '"')
        if ($i -lt $items.Count - 1) { [void]$sb.Append(',') }
        if (($i + 1) % $perLine -eq 0 -and $i -lt $items.Count - 1) { [void]$sb.Append("`n") }
        elseif ($i -lt $items.Count - 1) { [void]$sb.Append(' ') }
    }
    [void]$sb.Append("`n];`n")
    return $sb.ToString()
}

function Java-StrArray([string]$indent, [string]$decl, $items, [int]$perLine) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("$indent$decl = {`n")
    for ($i = 0; $i -lt $items.Count; $i++) {
        if ($i % $perLine -eq 0) { [void]$sb.Append($indent + '    ') }
        [void]$sb.Append('"' + (Esc-Java $items[$i]) + '"')
        if ($i -lt $items.Count - 1) { [void]$sb.Append(',') }
        if (($i + 1) % $perLine -eq 0 -and $i -lt $items.Count - 1) { [void]$sb.Append("`n") }
        elseif ($i -lt $items.Count - 1) { [void]$sb.Append(' ') }
    }
    [void]$sb.Append("`n$indent};`n")
    return $sb.ToString()
}

function Ring-LabelsRust($labels) {
    $parts = @()
    foreach ($l in $labels) { $parts += ('"' + (Esc-Rust $l) + '"') }
    return '&[' + ($parts -join ', ') + ']'
}

function Rust-Rings($rings) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("pub struct RingDef {`n")
    [void]$sb.Append("    pub id: &'static str,`n    pub text: &'static str,`n")
    [void]$sb.Append("    pub interval: f32,`n    pub offset: f32,`n")
    [void]$sb.Append("    pub labels: &'static [&'static str],`n}`n")
    [void]$sb.Append("pub const RINGS: [RingDef; $($rings.Count)] = [`n")
    foreach ($r in $rings) {
        $line = "    RingDef { id: `"$($r.id)`", text: `"$($r.text)`", interval: " +
            (Fmt-RustFloat ([double]$r.interval)) + ", offset: " + (Fmt-RustFloat ([double]$r.offset)) +
            ", labels: " + (Ring-LabelsRust $r.labels) + " },`n"
        [void]$sb.Append($line)
    }
    [void]$sb.Append("];`n")
    return $sb.ToString()
}

function Java-Rings($rings) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("    public static final class RingDef {`n")
    [void]$sb.Append("        public final String id;`n        public final String text;`n")
    [void]$sb.Append("        public final float interval;`n        public final float offset;`n")
    [void]$sb.Append("        public final String[] labels;`n")
    [void]$sb.Append("        RingDef(String id, String text, float interval, float offset, String[] labels) {`n")
    [void]$sb.Append("            this.id = id; this.text = text; this.interval = interval;`n")
    [void]$sb.Append("            this.offset = offset; this.labels = labels;`n        }`n    }`n")
    [void]$sb.Append("    public static final RingDef[] RINGS = {`n")
    foreach ($r in $rings) {
        $parts = @()
        foreach ($l in $r.labels) { $parts += ('"' + (Esc-Java $l) + '"') }
        $line = "        new RingDef(`"$($r.id)`", `"$($r.text)`", " +
            (Fmt-JavaFloat ([double]$r.interval)) + ", " + (Fmt-JavaFloat ([double]$r.offset)) +
            ", new String[]{" + ($parts -join ', ') + "}),`n"
        [void]$sb.Append($line)
    }
    [void]$sb.Append("    };`n")
    return $sb.ToString()
}

$json = Read-Utf8 $JsonPath
$doc = $json | ConvertFrom-Json
if ($doc.version -ne 2) { throw "mechanics.json version must be 2, got $($doc.version)" }
if ($doc.values.Count -ne 19) { throw "values[] must keep 19 items, got $($doc.values.Count)" }

$sha = (Get-FileHash $JsonPath -Algorithm SHA256).Hash.ToLower()

function ValueOf([string]$id) {
    foreach ($v in $doc.values) { if ($v.id -eq $id) { return [double]$v.value } }
    throw "values[] has no id '$id'"
}

function RingOf([int]$type) {
    foreach ($r in $doc.rings) { if ([int]$r.type -eq $type) { return $r } }
    throw "rings[] has no type $type"
}

# ---- Rust region -----------------------------------------------------------
$hdr = "// Source: app/mechanics.json (sha256: $sha). Regenerate: tools/mechanics/gen-mechanics.ps1"
$rust = New-Object System.Text.StringBuilder
[void]$rust.Append($hdr + "`n")
[void]$rust.Append("// Hand edits inside the markers are rejected by gen-mechanics.ps1 -Check.`n")
[void]$rust.Append((Rust-StrArray 'MOUNTAINS' $doc.tables.mountains 6))
[void]$rust.Append((Rust-StrArray 'WUXING' $doc.tables.wuxing 6))
[void]$rust.Append((Rust-StrArray 'YINYANG' $doc.tables.yinyang 6))
[void]$rust.Append((Rust-StrArray 'HOUTIAN_GUA' $doc.tables.houtian 6))
[void]$rust.Append((Rust-StrArray 'XIANTIAN_GUA' $doc.tables.xiantian 6))
[void]$rust.Append((Rust-StrArray 'JIAZI' $doc.tables.jiazi 10))
[void]$rust.Append((Rust-StrArray 'NAYIN' $doc.tables.nayin 10))
[void]$rust.Append((Rust-StrArray 'BRANCHES' $doc.tables.branches 12))
[void]$rust.Append((Rust-StrArray 'YANG_STEMS' $doc.tables.yang_stems 5))
[void]$rust.Append((Rust-StrArray 'YIN_STEMS' $doc.tables.yin_stems 5))
[void]$rust.Append("const GUA_WUXING: [(&str, &str); 8] = [`n")
$gw = @()
foreach ($p in $doc.tables.gua_wuxing) { $gw += '    ("' + (Esc-Rust $p[0]) + '", "' + (Esc-Rust $p[1]) + '"),' }
[void]$rust.Append(($gw -join "`n") + "`n];`n")
[void]$rust.Append((Rust-Rings $doc.rings))
[void]$rust.Append("`n")
[void]$rust.Append("// Ring scalars (values[] / rings[]). The reading formulas keep their inline`n")
[void]$rust.Append("// literals for now and are pinned to these by the #[cfg(test)] module in main.rs.`n")
[void]$rust.Append("const MOUNTAIN_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'degrees_per_mountain')) + ";`n")
[void]$rust.Append("const EARTH_OFFSET: f32 = " + (Fmt-RustFloat (ValueOf 'earth_plate_offset')) + ";`n")
[void]$rust.Append("const HUMAN_OFFSET: f32 = " + (Fmt-RustFloat (ValueOf 'human_plate_offset')) + ";`n")
[void]$rust.Append("const HEAVEN_OFFSET: f32 = " + (Fmt-RustFloat (ValueOf 'heaven_plate_offset')) + ";`n")
[void]$rust.Append("const FENJIN_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'fenjin_interval')) + ";`n")
[void]$rust.Append("const DRAGON72_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'dragon72_interval')) + ";`n")
[void]$rust.Append("const DRAGON60_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'dragon60_interval')) + ";`n")
[void]$rust.Append("#[allow(dead_code)] // pinned by the mechanics_pin test; the reading formula takes it in round B`n")
[void]$rust.Append("const DRAGON60_OFFSET: f32 = " + (Fmt-RustFloat ([double](RingOf 7).offset)) + ";`n")
[void]$rust.Append("const HEXAGRAM64_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'hexagram64_interval')) + ";`n")
[void]$rust.Append("const BAGUA_STEP: f32 = " + (Fmt-RustFloat (ValueOf 'bagua_interval')) + ";`n")
[void]$rust.Append("const ZHENG_XIANG_DEG: f32 = " + (Fmt-RustFloat (ValueOf 'zhengxiang_range')) + ";`n")

# ---- Java region -----------------------------------------------------------
$ind = '    '
$java = New-Object System.Text.StringBuilder
[void]$java.Append("$ind// Source: app/mechanics.json (sha256: $sha). Regenerate: tools/mechanics/gen-mechanics.ps1`n")
[void]$java.Append("$ind// Hand edits inside the markers are rejected by gen-mechanics.ps1 -Check.`n")
[void]$java.Append((Java-StrArray $ind 'public static final String[] MOUNTAINS' $doc.tables.mountains 6))
[void]$java.Append((Java-StrArray $ind 'public static final String[] WUXING' $doc.tables.wuxing 6))
[void]$java.Append((Java-StrArray $ind 'public static final String[] YINYANG' $doc.tables.yinyang 6))
[void]$java.Append((Java-StrArray $ind 'public static final String[] HOUTIAN' $doc.tables.houtian 6))
[void]$java.Append((Java-StrArray $ind 'public static final String[] XIANTIAN' $doc.tables.xiantian 6))
[void]$java.Append((Java-StrArray $ind 'public static final String[] JIAZI' $doc.tables.jiazi 10))
[void]$java.Append((Java-StrArray $ind 'public static final String[] NAYIN' $doc.tables.nayin 10))
[void]$java.Append((Java-StrArray $ind 'private static final String[] BRANCHES' $doc.tables.branches 12))
[void]$java.Append((Java-StrArray $ind 'private static final String[] YANG_STEMS' $doc.tables.yang_stems 5))
[void]$java.Append((Java-StrArray $ind 'private static final String[] YIN_STEMS' $doc.tables.yin_stems 5))

$dialNames = @()
foreach ($d in $doc.dials) { $dialNames += $d.text }
[void]$java.Append((Java-StrArray $ind 'public static final String[] COMPASS_TYPES' $dialNames 3))

$readTypes = @()
foreach ($r in $doc.rings) {
    $iv = [double]$r.interval
    $ivs = if ($iv -eq [math]::Floor($iv)) { [string]::Format($Inv, '{0}', $iv) } else { $iv.ToString('R', $Inv) }
    $readTypes += ([string]$r.text + ' ' + $ivs + [char]0x00B0)
}
[void]$java.Append((Java-StrArray $ind 'public static final String[] READ_TYPES' $readTypes 2))
[void]$java.Append((Java-Rings $doc.rings))

[void]$java.Append($ind + "// Ring scalars (values[] / rings[]).`n")
function JLine([string]$mods, [string]$name, [double]$v) {
    return $ind + $mods + ' float ' + $name + ' = ' + (Fmt-JavaFloat $v) + ";`n"
}
[void]$java.Append((JLine 'public static final' 'HEXAGRAM64_DEG' (ValueOf 'hexagram64_interval')))
[void]$java.Append((JLine 'static final' 'MOUNTAIN_STEP' (ValueOf 'degrees_per_mountain')))
[void]$java.Append((JLine 'static final' 'MOUNTAIN_HALF' ((ValueOf 'degrees_per_mountain') / 2.0)))
[void]$java.Append((JLine 'static final' 'EARTH_OFFSET' (ValueOf 'earth_plate_offset')))
[void]$java.Append((JLine 'static final' 'HUMAN_OFFSET' (ValueOf 'human_plate_offset')))
[void]$java.Append((JLine 'static final' 'HEAVEN_OFFSET' (ValueOf 'heaven_plate_offset')))
[void]$java.Append((JLine 'static final' 'FENJIN_STEP' (ValueOf 'fenjin_interval')))
[void]$java.Append((JLine 'static final' 'DRAGON72_STEP' (ValueOf 'dragon72_interval')))
[void]$java.Append((JLine 'static final' 'DRAGON60_STEP' (ValueOf 'dragon60_interval')))
[void]$java.Append((JLine 'static final' 'DRAGON60_OFFSET' ([double](RingOf 7).offset)))
[void]$java.Append((JLine 'static final' 'BAGUA_STEP' (ValueOf 'bagua_interval')))
[void]$java.Append((JLine 'static final' 'ZHENG_XIANG_DEG' (ValueOf 'zhengxiang_range')))

# ---- (Re)write the marked regions -----------------------------------------
function Build-Region([string]$lang, [string]$body) {
    $openLine = $Open
    $closeLine = $Close
    if ($lang -eq 'java') { $openLine = '    ' + $Open; $closeLine = '    ' + $Close }
    return $openLine + "`n" + $body + $closeLine + "`n"
}

function Get-Region([string]$text, [string]$path) {
    $marker = $text.IndexOf($Open)
    $end = $text.IndexOf($Close)
    if ($marker -lt 0 -or $end -lt 0 -or $end -lt $marker) { throw "missing GENERATED markers in $path" }
    # include the indentation of the BEGIN line
    $start = $text.LastIndexOf("`n", $marker) + 1
    $endLineEnd = $text.IndexOf("`n", $end)
    if ($endLineEnd -lt 0) { $endLineEnd = $text.Length - 1 }
    return @{ start = $start; end = $endLineEnd + 1; text = $text.Substring($start, $endLineEnd + 1 - $start) }
}

$regions = @(
    @{ path = $RustPath; region = (Build-Region 'rust' $rust.ToString()); lang = 'Rust' },
    @{ path = $JavaPath; region = (Build-Region 'java' $java.ToString()); lang = 'Java' }
)

$fail = 0
foreach ($item in $regions) {
    $text = Read-Utf8 $item.path
    $cur = Get-Region $text $item.path
    $same = ($cur.text -replace "`r`n", "`n") -eq ($item.region -replace "`r`n", "`n")
    if ($Check) {
        if ($same) { Write-Host "PASS  $($item.lang) generated region is up to date" }
        else { Write-Host "FAIL  $($item.lang) generated region is stale - run tools/mechanics/gen-mechanics.ps1"; $fail = 1 }
    } else {
        $new = $text.Substring(0, $cur.start) + $item.region + $text.Substring($cur.end)
        Write-Utf8 $item.path $new
        Write-Host "WROTE $($item.lang) generated region ($($item.region.Length) bytes)"
    }
}

if ($Check) {
    # Mirror the outer checker: every numeric values[] entry must still be visible
    # in the end sources, so the "19 values are used" red line keeps holding.
    $all = (Read-Utf8 $RustPath) + "`n" + (Read-Utf8 $JavaPath)
    foreach ($v in $doc.values) {
        if ($v.value -is [string]) { continue }
        $lit = ([double]$v.value).ToString('R', $Inv)
        if ($all.IndexOf($lit) -lt 0) {
            Write-Host "FAIL  value '$($v.name)' ($lit) does not appear in the end sources"
            $fail = 1
        }
    }
    if ($fail -eq 0) { Write-Host "PASS  all numeric values[] entries still appear in the end sources" }
    exit $fail
}
