<#
.SYNOPSIS
    Smoke test of the Windows build: installs the setup.exe silently (or uses an app image),
    opens the app on a sample notebook, drives it with the keyboard, saves screenshots and the
    app's log, then closes it and checks the exit code, the log, the picture shown in the
    reading view and the note it created.

.DESCRIPTION
    It takes the keyboard for about half a minute: run it on a machine nobody is using (the
    release workflow runs it on GitHub's Windows runner).

.EXAMPLE
    .\tools\smoke\smoke-windows.ps1 -Setup dist\release\Devava-Notes-1.0.0-windows-x64-setup.exe -Out target\smoke\out
    .\tools\smoke\smoke-windows.ps1 -Exe "dist\Devava Notes\Devava Notes.exe" -Out target\smoke\out
#>
param(
    [string]$Setup = "",
    [string]$Exe = "",
    [Parameter(Mandatory = $true)][string]$Out
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Out = (Resolve-Path $Out).Path

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Smoke {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr hwnd, int attr, out RECT rect, int size);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    // How many pixels have exactly this color, in 32-bit pixels stored as blue, green, red, alpha.
    public static int Count(byte[] bgra, byte r, byte g, byte b) {
        int n = 0;
        for (int i = 0; i + 3 < bgra.Length; i += 4)
            if (bgra[i] == b && bgra[i + 1] == g && bgra[i + 2] == r) n++;
        return n;
    }
}
"@

# --- A sample notebook: a note with a picture, a formula, a [[link]], a #tag and C++ ----------
$vault = Join-Path $Out "vault"
$notebook = Join-Path $vault "Smoke"
if (Test-Path $vault) { Remove-Item -Recurse -Force $vault }
New-Item -ItemType Directory -Force -Path (Join-Path $notebook "attachments") | Out-Null
$picture = New-Object System.Drawing.Bitmap 240, 120
$g = [System.Drawing.Graphics]::FromImage($picture); $g.Clear([System.Drawing.Color]::FromArgb(255, 0, 255)); $g.Dispose()
$picture.Save((Join-Path $notebook "attachments\magenta.png"), [System.Drawing.Imaging.ImageFormat]::Png); $picture.Dispose()
$utf8 = New-Object System.Text.UTF8Encoding $false
$sample = @'
See [[Other note]] and #smoke.

![](<attachments/magenta.png>)

$$
e^{i\pi} + 1 = 0
$$

```cpp
int main() { return 0; }
```
'@
[System.IO.File]::WriteAllText((Join-Path $notebook "Sample note.md"), $sample.Replace("`r`n", "`n"), $utf8)
[System.IO.File]::WriteAllText((Join-Path $notebook "Other note.md"), "Linked from the sample note.`n", $utf8)

if ($Setup) {
    $p = Start-Process -FilePath (Resolve-Path $Setup).Path -ArgumentList "/qn" -PassThru -Wait
    if ($p.ExitCode -ne 0) { throw "Installer exit code $($p.ExitCode)" }
    $Exe = Join-Path $env:LOCALAPPDATA "Devava Notes\Devava Notes.exe"
    Write-Host "Installed: $Exe"
}
if (-not (Test-Path $Exe)) { throw "Launcher not found: $Exe" }

$env:JAVA_TOOL_OPTIONS = "-Dnotes.home=$vault"
$app = Start-Process -FilePath $Exe -PassThru -RedirectStandardOutput "$Out\app.log" -RedirectStandardError "$Out\app.err.log"
Remove-Item Env:\JAVA_TOOL_OPTIONS
$app.Handle | Out-Null   # cache the handle so that ExitCode is available after the process ends

# The jpackage launcher re-runs itself as a child process, so the window belongs to a process
# other than $app: look it up by name and title ("Devava Notes", or "Smoke - Devava Notes").
function Window {
    for ($i = 0; $i -lt 60; $i++) {
        if (-not (Get-Process -Id $app.Id -ErrorAction SilentlyContinue)) { throw "The app exited before showing a window" }
        $proc = Get-Process -Name "Devava Notes" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "*Devava Notes" } | Select-Object -First 1
        if ($proc) { return $proc.MainWindowHandle }
        Start-Sleep -Seconds 1
    }
    throw "No window appeared"
}
function Shot($name) {
    $h = Window; [Smoke]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 400
    $r = New-Object Smoke+RECT
    [Smoke]::DwmGetWindowAttribute($h, 9, [ref]$r, [System.Runtime.InteropServices.Marshal]::SizeOf($r)) | Out-Null
    $bmp = New-Object System.Drawing.Bitmap ($r.Right - $r.Left), ($r.Bottom - $r.Top)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
    $g.Dispose(); $bmp.Save("$Out\$name.png"); $bmp.Dispose()
    Write-Host "screenshot: $name"
    return "$Out\$name.png"
}
# Keys for the main window, which is brought to the front first.
function Key($keys, $wait = 1) {
    $h = Window; [Smoke]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 200
    [System.Windows.Forms.SendKeys]::SendWait($keys); Start-Sleep -Seconds $wait
}
# Keys for the dialog the last key opened: it already has the focus.
function DialogKey($keys, $wait = 1) {
    [System.Windows.Forms.SendKeys]::SendWait($keys); Start-Sleep -Seconds $wait
}
# How many pixels of a screenshot are pure magenta, the color of the sample note's picture.
function Magenta($file) {
    $bmp = New-Object System.Drawing.Bitmap $file
    $data = $bmp.LockBits((New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height),
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bytes = New-Object byte[] ($data.Stride * $bmp.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $bmp.UnlockBits($data); $bmp.Dispose()
    return [Smoke]::Count($bytes, 255, 0, 255)
}

Window | Out-Null; Start-Sleep -Seconds 5    # the editor's page loads while Home shows
Shot "01-home" | Out-Null
Key "^o"                                      # Quick open
DialogKey "Sample note"; DialogKey "{ENTER}" 4          # opens the note, and its notebook
Shot "02-note" | Out-Null
Key "^e" 4                                    # the reading view: picture, formula, code
$reading = Shot "03-reading"
Key "^n"                                      # a new note: its title dialog has the focus
DialogKey "Written by the smoke test"; DialogKey "{ENTER}" 3
Shot "04-new-note" | Out-Null

$windowProc = Get-Process -Name "Devava Notes" | Where-Object { $_.MainWindowTitle -like "*Devava Notes" } | Select-Object -First 1
$windowProc.CloseMainWindow() | Out-Null
if (-not $app.WaitForExit(30000)) { Stop-Process -Name "Devava Notes" -Force; throw "The app did not exit after closing the window" }
Write-Host "app exit status: $($app.ExitCode)"
if ($app.ExitCode -ne 0) { Get-Content "$Out\app.err.log"; throw "Non-zero exit code" }
$log = (Get-Content "$Out\app.log" -Raw -ErrorAction SilentlyContinue) + (Get-Content "$Out\app.err.log" -Raw -ErrorAction SilentlyContinue)
if ($log -match "(?i)exception|\[editor\.js\]") { Write-Host $log; throw "The log reports an error" }
$magenta = Magenta $reading
Write-Host "magenta pixels in the reading view: $magenta"
if ($magenta -lt 5000) { throw "The picture of the note is not shown in the reading view" }
if (-not (Test-Path (Join-Path $notebook "Written by the smoke test.md"))) {
    Get-ChildItem $notebook; throw "The new note was not created"
}
Write-Host "Smoke test passed"
