param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Email = "",
  [securestring]$Password = (ConvertTo-SecureString "Passw0rd!123" -AsPlainText -Force),
  [switch]$KeepFamilyMember
)

$ErrorActionPreference = 'Stop'

function ConvertFrom-JsonSafe([string]$Raw) {
  if ($null -eq $Raw -or $Raw.Trim().Length -eq 0) { return $null }
  try {
    return ($Raw | ConvertFrom-Json -ErrorAction Stop)
  } catch {
    return $null
  }
}

if ($BaseUrl -notmatch '^https?://') {
  throw "Invalid BaseUrl '$BaseUrl'. Usage: .\\smoke-test.ps1 [-BaseUrl http://localhost:8080] [-Email you@dev.local] [-Password (ConvertTo-SecureString ...)]"
}

function New-TestEmail {
  $stamp = Get-Date -Format "yyyyMMddHHmmss"
  return "smoke+$stamp@dev.local"
}

function ConvertTo-NormalizedId($value) {
  if ($null -eq $value) { return "" }
  return ("$value").Trim().ToLowerInvariant()
}

function ConvertTo-JsonBody($obj) {
  return ($obj | ConvertTo-Json -Depth 12)
}

function ConvertFrom-SecureStringPlain([securestring]$Secure) {
  if ($null -eq $Secure) { return "" }
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
  try {
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  }
}

function Invoke-Api {
  param(
    [Parameter(Mandatory=$true)][string]$Method,
    [Parameter(Mandatory=$true)][string]$Path,
    [object]$Body = $null,
    [string]$Token = "",
    [hashtable]$Headers = @{}
  )

  $uri = ($BaseUrl.TrimEnd('/') + $Path)

  $allHeaders = @{}
  foreach ($k in $Headers.Keys) { $allHeaders[$k] = $Headers[$k] }
  if ($Token) { $allHeaders['Authorization'] = "Bearer $Token" }
  if ($null -ne $Body -and -not $allHeaders.ContainsKey('Content-Type')) { $allHeaders['Content-Type'] = 'application/json' }

  $payload = $null
  if ($null -ne $Body) {
    if ($Body -is [string]) { $payload = $Body } else { $payload = ConvertTo-JsonBody $Body }
  }

  try {
    $resp = Invoke-WebRequest -Method $Method -Uri $uri -Headers $allHeaders -Body $payload -UseBasicParsing
    return [pscustomobject]@{ Ok=$true; Status=$resp.StatusCode; Raw=$resp.Content; Json=(ConvertFrom-JsonSafe $resp.Content) }
  } catch {
    $ex = $_.Exception
    $status = $null
    $raw = ""

    if ($ex.Response -and $ex.Response.StatusCode) {
      try { $status = [int]$ex.Response.StatusCode } catch { }
      try {
        $sr = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
        $raw = $sr.ReadToEnd()
      } catch { }
    } else {
      $raw = ($_.ToString())
    }

    return [pscustomobject]@{ Ok=$false; Status=$status; Raw=$raw; Json=(ConvertFrom-JsonSafe $raw) }
  }
}

$results = New-Object System.Collections.Generic.List[object]
function Add-Result($Name, $Ok, $Status, $Details) {
  $results.Add([pscustomobject]@{ Name=$Name; Ok=$Ok; Status=$Status; Details=$Details }) | Out-Null
  if ($Ok) {
    Write-Host "[PASS] $Name" -ForegroundColor Green
  } else {
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    if ($null -ne $Status) { Write-Host "  Status: $Status" -ForegroundColor DarkRed }
    if ($Details) { Write-Host "  $Details" -ForegroundColor DarkRed }
  }
}

if (-not $Email) { $Email = New-TestEmail }

Write-Host "BaseUrl: $BaseUrl"
Write-Host "Email:   $Email"

$PasswordPlain = ConvertFrom-SecureStringPlain $Password

do {

# 0) Create dev doctor + login as doctor (to populate doctors table + test doctor endpoints)
$doctorEmail = ("doctor." + ($Email -replace "[@+].*","") + "@dev.local").ToLowerInvariant()
$createDoc = Invoke-Api -Method POST -Path "/auth/dev/create-doctor" -Body @{ email=$doctorEmail; name="Smoke Doctor"; password=$PasswordPlain }
$createDocOk = $createDoc.Ok -or ($createDoc.Status -eq 409)
Add-Result "dev.create-doctor" $createDocOk $createDoc.Status ($createDoc.Raw -replace "\s+"," ")
if (-not $createDocOk) { break }

$loginDoc = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$doctorEmail; password=$PasswordPlain }
$doctorToken = $null
if ($loginDoc.Ok -and $loginDoc.Json -and $loginDoc.Json.token) { $doctorToken = $loginDoc.Json.token }
Add-Result "auth.login.doctor" ($loginDoc.Ok -and $doctorToken) $loginDoc.Status ($loginDoc.Raw -replace "\s+"," ")
if (-not $doctorToken) { break }

# Seed professional portal demo data (consultations + doctor docs)
$seed = Invoke-Api -Method POST -Path "/auth/dev/seed-professional" -Body @{ doctorEmail=$doctorEmail; specialty="Cardiology"; yearsExperience=7; totalReviews=128; serviceRadiusKm=10; consultationsCount=6; documentsCount=2 }
Add-Result "dev.seed-professional" $seed.Ok $seed.Status ($seed.Raw -replace "\s+"," ")

# Basic doctor endpoints (these exercise doctors/consultations/doctor_documents tables)
$docProfile = Invoke-Api -Method GET -Path "/api/doctor/profile" -Token $doctorToken
$hasDocs = $docProfile.Ok -and $docProfile.Json -and $docProfile.Json.verificationDocuments
Add-Result "doctor.profile.get" $docProfile.Ok $docProfile.Status ($docProfile.Raw -replace "\s+"," ")

$goLive = Invoke-Api -Method PATCH -Path "/api/doctor/status" -Token $doctorToken -Body @{ goLive = $true }
Add-Result "doctor.status.goLive" $goLive.Ok $goLive.Status ($goLive.Raw -replace "\s+"," ")

$history = Invoke-Api -Method GET -Path "/api/doctor/history" -Token $doctorToken
$historyOk = $history.Ok -and $history.Json -and @($history.Json).Count -ge 1
Add-Result "doctor.history" $historyOk $history.Status ($history.Raw -replace "\s+"," ")
if (-not $historyOk) { break }

$revenue = Invoke-Api -Method GET -Path "/api/doctor/revenue?limit=5" -Token $doctorToken
$revenueOk = $revenue.Ok -and $revenue.Json -and $null -ne $revenue.Json.totalGrossEarnings -and $null -ne $revenue.Json.totalNetPart -and $null -ne $revenue.Json.omnicareCommission
Add-Result "doctor.revenue" $revenueOk $revenue.Status ($revenue.Raw -replace "\s+"," ")
if (-not $revenueOk) { break }

# 1) Create dev patient (idempotent-ish: accept 200 or 409)
$create = Invoke-Api -Method POST -Path "/auth/dev/create-patient" -Body @{ email=$Email; name="Smoke Test"; password=$PasswordPlain }
$createOk = $create.Ok -or ($create.Status -eq 409)
Add-Result "dev.create-patient" $createOk $create.Status ($create.Raw -replace "\s+"," ")
if (-not $createOk) { break }

# 2) Login
$login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$Email; password=$PasswordPlain }
$token = $null
if ($login.Ok -and $login.Json -and $login.Json.token) { $token = $login.Json.token }
Add-Result "auth.login" ($login.Ok -and $token) $login.Status ($login.Raw -replace "\s+"," ")
if (-not $token) { break }

# 3) Account me
$me = Invoke-Api -Method GET -Path "/api/account/me" -Token $token
Add-Result "account.me" $me.Ok $me.Status ($me.Raw -replace "\s+"," ")
if (-not $me.Ok) { break }

# 4) Full profile
$full = Invoke-Api -Method GET -Path "/api/account/full-profile" -Token $token
Add-Result "account.full-profile" $full.Ok $full.Status "(len=$($full.Raw.Length))"

# 5) Patients list
$patients = Invoke-Api -Method GET -Path "/api/patients" -Token $token
$patientId = $null
if ($patients.Ok -and $patients.Json) {
  $list = @($patients.Json)
  if ($list.Count -gt 0) {
    $userPatient = $list | Where-Object { $_.type -eq 'USER' } | Select-Object -First 1
    if (-not $userPatient) { $userPatient = $list | Select-Object -First 1 }
    if ($userPatient) { $patientId = $userPatient.patientId }
  }
}
if ($patients.Ok -and $patientId) {
  Add-Result "patients.list" $true $patients.Status ("patientId=" + ($patientId | Out-String).Trim())
} else {
  Add-Result "patients.list" $false $patients.Status ("patientId=" + ($patientId | Out-String).Trim() + " raw=" + ($patients.Raw -replace "\s+"," "))
  break
}

# 6) Medication search + getById
$medSearch = Invoke-Api -Method GET -Path ("/api/medications/search?q=" + [uri]::EscapeDataString("para")) -Token $token
$medId = $null
if ($medSearch.Ok -and $medSearch.Json -and $medSearch.Json.Count -gt 0) {
  $medId = $medSearch.Json[0].id
}
Add-Result "medications.search" ($medSearch.Ok -and $medId) $medSearch.Status ("firstMedId=" + ($medId | Out-String).Trim())

if ($medId) {
  $medById = Invoke-Api -Method GET -Path ("/api/medications/" + $medId) -Token $token
  Add-Result "medications.getById" $medById.Ok $medById.Status ($medById.Raw -replace "\s+"," ")
}

$medDci = Invoke-Api -Method GET -Path ("/api/medications/dci?q=" + [uri]::EscapeDataString("amo")) -Token $token
$hasDciList = $medDci.Ok -and ($medDci.Json -is [System.Collections.IEnumerable])
Add-Result "medications.dci" $hasDciList $medDci.Status "(ok)"

# 7) Allergies CRUD (structured)
$allergyCreatedId = $null
$addAllergy = Invoke-Api -Method POST -Path ("/api/patients/$patientId/allergies") -Token $token -Body @{ substance="Penicillin"; reaction="Rash"; severity="HIGH" }
if ($addAllergy.Ok -and $addAllergy.Json -and $addAllergy.Json.id) { $allergyCreatedId = $addAllergy.Json.id }
$allergyCreatedIdN = ConvertTo-NormalizedId $allergyCreatedId
Add-Result "patients.allergies.add" ($addAllergy.Ok -and $allergyCreatedIdN) $addAllergy.Status ("createdId=" + $allergyCreatedIdN + " raw=" + ($addAllergy.Raw -replace "\s+"," "))

$listAll = Invoke-Api -Method GET -Path ("/api/patients/$patientId/allergies") -Token $token
$present = $false
if ($listAll.Ok -and $listAll.Json) {
  $list = @($listAll.Json)
  $present = ($list | Where-Object { (ConvertTo-NormalizedId $_.id) -eq $allergyCreatedIdN }).Count -gt 0
}
if (-not $present -and $listAll.Ok -and $listAll.Raw) {
  $present = $listAll.Raw -match ('"id"\s*:\s*"' + [regex]::Escape($allergyCreatedIdN) + '"')
}
if ($listAll.Ok -and $present) {
  Add-Result "patients.allergies.list" $true $listAll.Status "(created found=$present)"
} else {
  Add-Result "patients.allergies.list" $false $listAll.Status ("(createdId=" + $allergyCreatedIdN + ", found=$present) raw=" + ($listAll.Raw -replace "\s+"," "))
}

if ($allergyCreatedId) {
  $delAll = Invoke-Api -Method DELETE -Path ("/api/patients/$patientId/allergies/$allergyCreatedId") -Token $token
  Add-Result "patients.allergies.delete" $delAll.Ok $delAll.Status ""
}

# 8) Medical passport patch for user
if (-not $medId) {
  # fall back: don't include currentMedications if we couldn't find a med
  $passportBody = @{ bloodGroup = "A_POS"; medicalInfo = @{ chronicConditions = @("Diabetes") ; allergicDci = @("amoxicillin") } }
} else {
  $passportBody = @{ bloodGroup = "A_POS"; medicalInfo = @{ chronicConditions = @("Diabetes") ; allergicDci = @("amoxicillin"); currentMedications = @(@{ medicationId = "$medId"; dosage = ""; frequency = "" }) } }
}

$patchPassport = Invoke-Api -Method PATCH -Path "/api/account/medical-passport" -Token $token -Body $passportBody
Add-Result "account.medical-passport.patch" $patchPassport.Ok $patchPassport.Status ""

# 9) Family member CRUD + medical passport
$fmId = $null
$createFm = Invoke-Api -Method POST -Path "/api/family" -Token $token -Body @{ fullName="Test Family"; relationship="SPOUSE"; birthDate="1990-01-01"; gender="F"; medicalInfo=@{} }
if ($createFm.Ok -and $createFm.Json -and $createFm.Json.id) { $fmId = $createFm.Json.id }
$fmIdN = ConvertTo-NormalizedId $fmId
Add-Result "family.create" ($createFm.Ok -and $fmIdN) $createFm.Status ("createdId=" + $fmIdN + " raw=" + ($createFm.Raw -replace "\s+"," "))

$listFm = Invoke-Api -Method GET -Path "/api/family" -Token $token
$inList = $false
if ($listFm.Ok -and $listFm.Json) {
  $list = @($listFm.Json)
  $inList = ($list | Where-Object { (ConvertTo-NormalizedId $_.id) -eq $fmIdN }).Count -gt 0
}
if (-not $inList -and $listFm.Ok -and $listFm.Raw) {
  $inList = $listFm.Raw -match ('"id"\s*:\s*"' + [regex]::Escape($fmIdN) + '"')
}
if ($listFm.Ok -and $inList) {
  Add-Result "family.list" $true $listFm.Status "(created found=$inList)"
} else {
  Add-Result "family.list" $false $listFm.Status ("(createdId=" + $fmIdN + ", found=$inList) raw=" + ($listFm.Raw -replace "\s+"," "))
}

if ($fmId) {
  $patchFm = Invoke-Api -Method PATCH -Path ("/api/family/$fmId") -Token $token -Body @{ fullName="Test Family"; relationship="CHILD"; birthDate="1990-01-01"; gender="F"; medicalInfo=@{} }
  Add-Result "family.patch" $patchFm.Ok $patchFm.Status ""

  $patchFmPassport = Invoke-Api -Method PATCH -Path ("/api/family/$fmId/medical-passport") -Token $token -Body @{ bloodGroup = "O_POS"; medicalInfo = @{ chronicConditions=@("Asthma") } }
  Add-Result "family.medical-passport.patch" $patchFmPassport.Ok $patchFmPassport.Status ""

  if (-not $KeepFamilyMember) {
    $delFm = Invoke-Api -Method DELETE -Path ("/api/family/$fmId") -Token $token
    Add-Result "family.delete" $delFm.Ok $delFm.Status ""
  } else {
    Add-Result "family.delete" $true $null "(skipped)"
  }
}

} while ($false)

Write-Host ""
Write-Host "================ SUMMARY ================"
$results | Select-Object Name, Ok, Status | Format-Table -AutoSize

$failed = $results | Where-Object { -not $_.Ok }
if ($failed.Count -gt 0) {
  Write-Host "\nFailed tests details:" -ForegroundColor Red
  foreach ($f in $failed) {
    Write-Host "- $($f.Name): status=$($f.Status)" -ForegroundColor Red
    if ($f.Details) { Write-Host "  $($f.Details)" -ForegroundColor DarkRed }
  }
  exit 1
}

exit 0
