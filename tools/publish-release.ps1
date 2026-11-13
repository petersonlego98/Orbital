<#
.SYNOPSIS
    Publishes (or updates) a GitHub release from the notes versioned in docs/.

.DESCRIPTION
    The release notes live in docs/RELEASE_NOTES_<version>.md so a release never depends
    on a shell session: no encoding surprises, no drift between the tag and what the
    player reads.

    Two encoding rules are enforced here, because both already broke a release:

      1. The notes are read with [System.IO.File]::ReadAllText(..., UTF8). PowerShell 5.1
         Get-Content defaults to the ANSI code page and silently mangles accents ("Ã¡rea").
      2. The JSON body is sent as UTF8 *bytes*. Passing a string makes Invoke-WebRequest
         re-encode it with the console code page.

    Credentials come from git (Git Credential Manager), or from -Token.

.PARAMETER Version
    Release version, with or without the leading "v" (e.g. 1.1.0 or v1.1.0).

.PARAMETER Repo
    owner/repo. Defaults to the origin remote of the current repository.

.PARAMETER Commit
    Commit the tag points at. Defaults to HEAD.

.PARAMETER Assets
    Extra files to upload alongside the release.

.PARAMETER Latest
    Mark the release as "latest".

.EXAMPLE
    pwsh tools\publish-release.ps1 -Version 1.1.0 -Assets build\installer\Orbital-1.1.0-win64-usuario.zip
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Repo,
    [string]$Commit = "",
    [string[]]$Assets,
    [switch]$Latest
)

#"HEAD" is not a commitish the API accepts: resolve it to the real sha.
if(-not $Commit){ $Commit = (git rev-parse HEAD) }

$ErrorActionPreference = "Stop"

$tag = if($Version.StartsWith("v")){ $Version } else { "v$Version" }
$notes = Join-Path $PSScriptRoot "..\docs\RELEASE_NOTES_$tag.md"
if(-not (Test-Path $notes)){
    throw "Notas nao encontradas: $notes (crie docs/RELEASE_NOTES_$tag.md)"
}

if(-not $Repo){
    $url = git remote get-url origin
    if($url -match "github\.com[:/](.+?)(?:\.git)?/?$"){
        $Repo = $Matches[1]
    }else{
        throw "Nao foi possivel detectar owner/repo a partir do remote: $url"
    }
}

#Credentials: -Token wins; otherwise ask git (Git Credential Manager).
$token = $env:GITHUB_TOKEN
if(-not $token){
    $fill = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
    if($fill){
        $line = $fill | Select-String -Pattern "^password=" | Select-Object -First 1
        if($line){ $token = ($line.Line -replace "^password=","").Trim() }
    }
}
if(-not $token){ throw "Sem credencial: use -Token ou configure o Git Credential Manager." }

$headers = @{
    Authorization        = "Bearer $token"
    Accept               = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}

#--- JSON helpers -------------------------------------------------------------
#A hand-rolled escaper keeps the notes byte-for-byte identical to the file: no provider
#objects, no console encoding in the middle.
function Esc($text){
    $text = $text -replace '\\','\\'
    $text = $text -replace '"','\"'
    $text = $text -replace "`r`n","\n"
    $text = $text -replace "`n","\n"
    $text = $text -replace "`r","\n"
    $text = $text -replace "`t","\t"
    return $text
}

function Send-GitHub($method, $uri, $body){
    return Invoke-WebRequest -Uri $uri -Headers $headers -Method $method -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
}

#--- payload ------------------------------------------------------------------
$body = [System.IO.File]::ReadAllText($notes, [System.Text.Encoding]::UTF8)
$name = ($body -split "`n" | Where-Object { $_.StartsWith("# ") } | Select-Object -First 1)
if($name){ $name = $name.Substring(2).Trim() } else { $name = "Orbital $tag" }
#target_commitish is only accepted when creating a release; a PATCH with it is a 422
$payload = '{"name":"' + (Esc $name) + '","body":"' + (Esc $body) + '","draft":false,"prerelease":false'
if($Latest){ $payload += ',"make_latest":"true"' }
$payload += '}'
$createPayload = '{"tag_name":"' + (Esc $tag) + '","target_commitish":"' + (Esc $Commit) + '",' + $payload.Substring(1)

$releaseUri = "https://api.github.com/repos/$Repo/releases/tags/$tag"
$existing = $null
try{ $existing = Invoke-RestMethod -Uri $releaseUri -Headers $headers -Method Get }catch{}

if($existing){
    Write-Host "Release $tag ja existe: atualizando o corpo..."
    $existingid = $existing.id
    Send-GitHub Patch "https://api.github.com/repos/$Repo/releases/$existingid" $payload | Out-Null
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/$existingid" -Headers $headers
}else{
    Write-Host "Criando release $tag em $Repo..."
    $response = Send-GitHub Post "https://api.github.com/repos/$Repo/releases" $createPayload
    $release = $response.Content | ConvertFrom-Json
}

Write-Host "Release: $($release.html_url)"
Write-Host "Nome:   $($release.name)"
Write-Host "Corpo:  $($release.body.Length) caracteres"

if($Assets){
    foreach($asset in $Assets){
        if(-not (Test-Path $asset)){ throw "Asset nao encontrado: $asset" }
        $name = Split-Path $asset -Leaf
        $existingAsset = $release.assets | Where-Object { $_.name -eq $name } | Select-Object -First 1
        if($existingAsset){
            Send-GitHub Delete "https://api.github.com/repos/$Repo/releases/assets/$($existingAsset.id)" "{}" | Out-Null
        }
        #PowerShell 5.1 sends -InFile as application/x-www-form-urlencoded, which the
        #uploads endpoint rejects ("content_type can't be ..."): name the type explicitly.
        $extension = [System.IO.Path]::GetExtension($asset).ToLower()
        $contentType = switch($extension){
            ".zip" { "application/zip" }
            ".apk" { "application/vnd.android.package-archive" }
            ".sha256" { "text/plain" }
            default { "application/octet-stream" }
        }
        $upload = "https://uploads.github.com/repos/$Repo/releases/$($release.id)/assets?name=$([Uri]::EscapeDataString($name))"
        Invoke-WebRequest -Uri $upload -Headers $headers -Method Post -InFile $asset -ContentType $contentType -UseBasicParsing | Out-Null
        Write-Host "Asset:   $name ($contentType)"
    }
}

if($Latest){ Send-GitHub Patch "https://api.github.com/repos/$Repo/releases/$($release.id)" '{"make_latest":"true"}' | Out-Null }
