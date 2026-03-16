param(
  [string]$BaseUrl = "http://localhost:8080",
  [securestring]$Password = (ConvertTo-SecureString "Passw0rd!123" -AsPlainText -Force),
  [int]$DoctorsCount = 10,
  [int]$NursesCount = 5,
  [int]$KinesCount = 3,
  [int]$PsychiatristsCount = 2,
  [double]$DoctorOnlineChance = 0.40,
  [int]$MinPatientsPerProfessional = 5,
  [int]$PatientsCount = 50,
  [double]$FamilyChance = 0.60,
  [int]$MinFamilyMembers = 1,
  [int]$MaxFamilyMembers = 5
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

function Maybe-AddChronicConditions([string]$token, [string]$patientId) {
  if (-not $token -or -not $patientId) { return }

  $chronicPool = @('Diabetes','Hypertension','Asthma','Thyroid Disorder','Migraines','High Cholesterol')

  # ~91% chance to have at least one chronic condition (matches the old intent), else none.
  $addChronic = (Get-Random -Minimum 0 -Maximum 100) -lt 55
  if (-not $addChronic) {
    $force = (Get-Random -Minimum 0 -Maximum 100) -lt 80
    if ($force) {
      $addChronic = $true
    }
  }
  if (-not $addChronic) { return }

  $ccCount = Get-Random -Minimum 1 -Maximum 3
  $chosen = @($chronicPool | Get-Random -Count $ccCount)
  foreach ($name in $chosen) {
    if (-not $name) { continue }
    $add = Invoke-Api -Method POST -Path "/api/patients/$patientId/conditions" -Token $token -Body @{ name=$name; notes="" }
    if (-not $add.Ok) {
      continue
    }
  }
}

function ConvertTo-JsonBody($obj) {
  return ($obj | ConvertTo-Json -Depth 20)
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

function Read-NameList([string]$path) {
  if (-not (Test-Path $path)) { throw "Missing file: $path" }
  return Get-Content -LiteralPath $path | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_.Length -gt 0 }
}

function Get-RandItem([object[]]$list) {
  if ($null -eq $list -or $list.Count -lt 1) { return $null }
  return $list | Get-Random
}

function Normalize-Token([string]$v) {
  if (-not $v) { return "" }
  $s = $v.Trim().ToLowerInvariant()
  $s = $s -replace "'",""
  $s = $s -replace "\s+","."
  $s = $s -replace "[^a-z0-9._-]",""
  $s = $s -replace "\.+","."
  $s = $s.Trim('.')
  return $s
}

function New-PatientEmail([string]$firstName, [string]$lastName) {
  $f = Normalize-Token $firstName
  $l = Normalize-Token $lastName
  $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if (-not $f) { $f = "user" }
  if (-not $l) { $l = "test" }
  return "$f.$l+$stamp@example.tn"
}

function New-DoctorEmail([string]$firstName, [string]$lastName) {
  $f = Normalize-Token $firstName
  $l = Normalize-Token $lastName
  $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if (-not $f) { $f = "doctor" }
  if (-not $l) { $l = "test" }
  return "dr.$f.$l+$stamp@dev.local"
}

function New-ProfessionalEmail([string]$role, [string]$firstName, [string]$lastName) {
  $f = Normalize-Token $firstName
  $l = Normalize-Token $lastName
  $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if (-not $f) { $f = "pro" }
  if (-not $l) { $l = "test" }
  $r = Normalize-Token $role
  if (-not $r) { $r = "pro" }
  return "$r.$f.$l+$stamp@dev.local"
}

function New-TunisPhone() {
  $starts = @('2','5','9')
  $start = Get-Random -InputObject $starts
  $rest = Get-Random -Minimum 0 -Maximum 10000000
  return ($start + ($rest.ToString('0000000')))
}

function New-RandomBirthDate([int]$minAge, [int]$maxAge) {
  $age = Get-Random -Minimum $minAge -Maximum ($maxAge + 1)
  $days = Get-Random -Minimum 0 -Maximum 365
  return (Get-Date).Date.AddYears(-$age).AddDays(-$days)
}

function New-RandomBloodGroup() {
  $all = @('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG','O_POS','O_NEG')
  return Get-Random -InputObject $all
}

function Merge-MedicalInfo([hashtable]$baseInfo, [hashtable]$patchInfo) {
  $out = @{}
  if ($baseInfo) {
    foreach ($k in $baseInfo.Keys) { $out[$k] = $baseInfo[$k] }
  }
  if ($patchInfo) {
    foreach ($k in $patchInfo.Keys) { $out[$k] = $patchInfo[$k] }
  }
  return $out
}

function Pick-Medication([string]$token) {
  $queries = @('para','met','amo','vita','ibu','asp','ome','ins')
  $q = Get-Random -InputObject $queries
  $resp = Invoke-Api -Method GET -Path ("/api/medications/search?q=" + [uri]::EscapeDataString($q)) -Token $token
  if (-not $resp.Ok -or -not $resp.Json) { return $null }

  $arr = @($resp.Json)
  if ($arr.Count -lt 1) { return $null }

  $first = $arr | Select-Object -First 1
  if ($first -and $first.id) { return $first }
  return $null
}

function New-RandomMedicationFrequency() {
  $freq = Get-Random -Minimum 1 -Maximum 4
  $when = @('Morning','Evening','After meals','Before sleep') | Get-Random
  return "${freq}x/day ($when)"
}

function New-RandomMedicationDuration() {
  $choices = @('5 days','7 days','10 days','14 days','30 days','Ongoing')
  return Get-Random -InputObject $choices
}

function Add-PatientMedications([string]$token, [string]$patientId, [ref]$medsCounter) {
  if (-not $token -or -not $patientId) { return }

  # 70% chance to have current meds; if yes 1-3
  $hasMeds = (Get-Random -Minimum 0 -Maximum 100) -lt 70
  if (-not $hasMeds) { return }

  $mCount = Get-Random -Minimum 1 -Maximum 4
  for ($i=0; $i -lt $mCount; $i++) {
    $med = Pick-Medication -token $token
    if ($null -eq $med -or -not $med.id) { continue }

    $times = Get-Random -Minimum 1 -Maximum 4
    $freq = New-RandomMedicationFrequency
    $duration = Get-Random -Minimum 5 -Maximum 31
    $start = (Get-Date).Date.AddDays(-(Get-Random -Minimum 0 -Maximum 60)).ToString('yyyy-MM-dd')

    $add = Invoke-Api -Method POST -Path "/api/patients/$patientId/medications" -Token $token -Body @{
      medicationId = "$($med.id)"
      timesPerDay = $times
      frequency = $freq
      durationDays = $duration
      startDate = $start
      notes = ""
    }

    if ($add.Ok) {
      $medsCounter.Value++
    }
  }
}

function Build-CurrentMedicationItem($medication) {
  if ($null -eq $medication -or -not $medication.id) { return $null }
  $item = @{
    medicationId = "$($medication.id)"
    frequency = (New-RandomMedicationFrequency)
    duration = (New-RandomMedicationDuration)
  }
  if ($medication.name) { $item['name'] = $medication.name }
  if ($medication.dosage) { $item['dosage'] = $medication.dosage }
  if ($medication.form) { $item['form'] = $medication.form }
  return $item
}

function Get-ExistingMedicalInfo([string]$token, [string]$path) {
  # user medical passport
  if ($path -eq '/api/account/medical-passport') {
    $current = Invoke-Api -Method GET -Path "/api/account/full-profile" -Token $token
    if ($current.Ok -and $current.Json -and $current.Json.data -and $current.Json.data.user -and $current.Json.data.user.medicalInfo) {
      try {
        $existing = @{}
        foreach ($k in $current.Json.data.user.medicalInfo.Keys) { $existing[$k] = $current.Json.data.user.medicalInfo[$k] }
        return $existing
      } catch { return @{} }
    }
    return @{}
  }

  # family member medical passport
  if ($path -match '^/api/family/([0-9a-fA-F\-]{36})/medical-passport$') {
    $fmId = $Matches[1]
    $list = Invoke-Api -Method GET -Path "/api/family" -Token $token
    if ($list.Ok -and $list.Json) {
      $arr = @($list.Json)
      $row = $arr | Where-Object { ("$($_.id)") -eq $fmId } | Select-Object -First 1
      if ($row -and $row.medicalInfo) {
        try {
          $existing = @{}
          foreach ($k in $row.medicalInfo.Keys) { $existing[$k] = $row.medicalInfo[$k] }
          return $existing
        } catch { return @{} }
      }
    }
    return @{}
  }

  return @{}
}

function Maybe-AddAllergies([string]$token, [string]$patientId, [ref]$allergiesCounter) {
  # 65% chance to have allergies; if yes 1-3
  $has = (Get-Random -Minimum 0 -Maximum 100) -lt 65
  if (-not $has) { return }

  $substances = @('Penicillin','Peanuts','Pollen','Dust','Latex','Ibuprofen')
  $count = Get-Random -Minimum 1 -Maximum 4
  $chosen = $substances | Get-Random -Count $count

  foreach ($s in $chosen) {
    $sev = @('LOW','MEDIUM','HIGH') | Get-Random
    $add = Invoke-Api -Method POST -Path "/api/patients/$patientId/allergies" -Token $token -Body @{ substance=$s; reaction=""; severity=$sev }
    if ($add.Ok) {
      $allergiesCounter.Value++
    }
  }
}

function Maybe-PatchMedicalPassport([string]$token, [string]$path, [ref]$medsCounter) {
  $medicalInfo = @{}
  if ($medicalInfo.Keys.Count -lt 1) { return }

  $existing = Get-ExistingMedicalInfo -token $token -path $path
  $merged = Merge-MedicalInfo -baseInfo $existing -patchInfo $medicalInfo
  $body = @{ medicalInfo = $merged }
  $patch = Invoke-Api -Method PATCH -Path $path -Token $token -Body $body
  if (-not $patch.Ok) {
    return
  }
}

if ($BaseUrl -notmatch '^https?://') {
  throw "Invalid BaseUrl '$BaseUrl'. Usage: .\populate-realistic-patients.ps1 [-BaseUrl http://localhost:8080]"
}

$PasswordPlain = ConvertFrom-SecureStringPlain $Password

$root = $PSScriptRoot
$maleNames = Read-NameList (Join-Path $root 'male_names.txt')
$femaleNames = Read-NameList (Join-Path $root 'female_names.txt')
$lastNames = Read-NameList (Join-Path $root 'last_names.txt')

Write-Host "BaseUrl: $BaseUrl"
Write-Host "DoctorsCount: $DoctorsCount"
Write-Host "NursesCount: $NursesCount"
Write-Host "KinesCount: $KinesCount"
Write-Host "PsychiatristsCount: $PsychiatristsCount"
Write-Host "PatientsCount: $PatientsCount"

$createdDoctors = 0
$createdNurses = 0
$createdKines = 0
$createdPsychiatrists = 0
$createdUsers = 0
$createdFamily = 0
$allergiesAdded = 0
$medsAdded = 0

$failures = New-Object System.Collections.Generic.List[object]

$professionalAccounts = New-Object System.Collections.Generic.List[object]
$professionalTokenCache = @{}
$grantsPerProfessional = @{}
$grantRoundRobinIndex = 0

function Get-AuthTokenFromLoginResponse($loginJson) {
  if ($null -eq $loginJson) { return $null }
  if ($loginJson.token) { return $loginJson.token }
  if ($loginJson.data -and $loginJson.data.accessToken) { return $loginJson.data.accessToken }
  return $null
}

function Get-ProfessionalToken([string]$email) {
  if (-not $email) { return $null }
  if ($professionalTokenCache.ContainsKey($email)) { return $professionalTokenCache[$email] }

  $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$email; password=$PasswordPlain }
  $token = Get-AuthTokenFromLoginResponse $login.Json
  if (-not $token) { return $null }
  $professionalTokenCache[$email] = $token
  return $token
}

function Grant-RandomProfessionalAccessToPatient([string]$patientToken, [string]$patientId) {
  if ($professionalAccounts.Count -lt 1) { return }
  if (-not $patientToken -or -not $patientId) { return }

  $pick = $professionalAccounts | Get-Random
  if ($null -eq $pick -or -not $pick.email) { return }

  $share = Invoke-Api -Method POST -Path "/api/access/share-token" -Token $patientToken -Body @{ patientId=$patientId; ttlSeconds=2592000 }
  if (-not $share.Ok -or -not $share.Json -or -not $share.Json.token) { return }

  $proToken = Get-ProfessionalToken -email $pick.email
  if (-not $proToken) { return }

  $redeem = Invoke-Api -Method POST -Path "/api/access/redeem" -Token $proToken -Body @{ token=$share.Json.token }
  if (-not $redeem.Ok) { return }
}

function Get-NextProfessionalNeedingGrants() {
  if ($professionalAccounts.Count -lt 1) { return $null }

  $tries = 0
  while ($tries -lt $professionalAccounts.Count) {
    $idx = $grantRoundRobinIndex % $professionalAccounts.Count
    $grantRoundRobinIndex++
    $tries++

    $p = $professionalAccounts[$idx]
    if ($null -eq $p -or -not $p.email) { continue }

    if (-not $grantsPerProfessional.ContainsKey($p.email)) { $grantsPerProfessional[$p.email] = 0 }
    $current = [int]$grantsPerProfessional[$p.email]
    if ($current -lt $MinPatientsPerProfessional) { return $p }
  }

  return $null
}

function Grant-ProfessionalAccessToPatient([string]$patientToken, [string]$patientId, [string]$professionalEmail) {
  if (-not $patientToken -or -not $patientId -or -not $professionalEmail) { return $false }

  $share = Invoke-Api -Method POST -Path "/api/access/share-token" -Token $patientToken -Body @{ patientId=$patientId; ttlSeconds=2592000 }
  if (-not $share.Ok -or -not $share.Json -or -not $share.Json.token) { return $false }

  $proToken = Get-ProfessionalToken -email $professionalEmail
  if (-not $proToken) { return $false }

  $redeem = Invoke-Api -Method POST -Path "/api/access/redeem" -Token $proToken -Body @{ token=$share.Json.token }
  if (-not $redeem.Ok) { return $false }
  return $true
}

function Create-Professional([string]$role, [string]$fullName, [string]$email, [string]$firstName, [string]$lastName, [string]$phone, [string]$dob, [string]$gender, [string]$specialty, [int]$yearsExp, [int]$radius, [int]$reviews, [double]$rating, [bool]$isOnline) {
  $resp = Invoke-Api -Method POST -Path "/auth/dev/create-professional" -Body @{
    email=$email
    name=$fullName
    password=$PasswordPlain
    role=$role
    firstName=$firstName
    lastName=$lastName
    phoneNumber=$phone
    dateOfBirth=$dob
    gender=$gender
    specialty=$specialty
    yearsExperience=$yearsExp
    serviceRadiusKm=$radius
    totalReviews=$reviews
    rating=$rating
    isOnline=$isOnline
  }
  return $resp
}

if ($DoctorsCount -gt 0) {
  $specialties = @(
    'Cardiology','Dermatology','General Practice','Pediatrics','Neurology',
    'Orthopedics','Gynecology','ENT','Psychiatry','Endocrinology'
  )

  for ($di = 1; $di -le $DoctorsCount; $di++) {
    $isFemaleDoc = (Get-Random -Minimum 0 -Maximum 2) -eq 0
    if ($isFemaleDoc) {
      $dfirst = Get-RandItem $femaleNames
    } else {
      $dfirst = Get-RandItem $maleNames
    }
    $dlast = Get-RandItem $lastNames

    if (-not $dfirst -or -not $dlast) {
      $failures.Add([pscustomobject]@{ Step='pickDoctorName'; PatientIndex=$di; Details='Empty name list' }) | Out-Null
      continue
    }

    $dFullName = "Dr. $dfirst $dlast"
    $dEmail = New-DoctorEmail -firstName $dfirst -lastName $dlast
    $dPhone = New-TunisPhone
    $dDob = (New-RandomBirthDate -minAge 26 -maxAge 65).ToString('yyyy-MM-dd')
    if ($isFemaleDoc) { $dGender = 'F' } else { $dGender = 'M' }

    $spec = Get-Random -InputObject $specialties
    $yearsExp = Get-Random -Minimum 1 -Maximum 36
    $radius = Get-Random -Minimum 2 -Maximum 26
    $reviews = Get-Random -Minimum 0 -Maximum 401
    $rating = [Math]::Round((Get-Random -Minimum 40 -Maximum 51) / 10.0, 1)
    $isOnline = (Get-Random -Minimum 0 -Maximum 100) -lt [int]($DoctorOnlineChance * 100)

    $createDoc = Invoke-Api -Method POST -Path "/auth/dev/create-doctor" -Body @{
      email=$dEmail
      name=$dFullName
      password=$PasswordPlain
      firstName=$dfirst
      lastName=$dlast
      phoneNumber=$dPhone
      dateOfBirth=$dDob
      gender=$dGender
      specialty=$spec
      yearsExperience=$yearsExp
      serviceRadiusKm=$radius
      totalReviews=$reviews
      rating=$rating
      isOnline=$isOnline
    }

    if (-not $createDoc.Ok -and $createDoc.Status -ne 409) {
      $failures.Add([pscustomobject]@{ Step='createDoctor'; PatientIndex=$di; Details=($createDoc.Raw -replace "\s+"," ") }) | Out-Null
      continue
    }

    $createdDoctors++
    $professionalAccounts.Add([pscustomobject]@{ role='DOCTOR'; name=$dFullName; email=$dEmail }) | Out-Null
    Write-Host ("Created Doctor: $dFullName ($spec)") -ForegroundColor Cyan
  }
}

if ($NursesCount -gt 0 -or $KinesCount -gt 0 -or $PsychiatristsCount -gt 0) {
  $specByRole = @{
    'NURSE' = @('Home Care','Emergency','Pediatrics','General')
    'KINE' = @('Physiotherapy','Sports Rehab','Orthopedics')
    'PSYCHIATRIST' = @('Psychiatry','Mental Health')
  }

  $roleCounts = @{
    'NURSE' = $NursesCount
    'KINE' = $KinesCount
    'PSYCHIATRIST' = $PsychiatristsCount
  }

  foreach ($role in $roleCounts.Keys) {
    $count = [int]$roleCounts[$role]
    if ($count -le 0) { continue }

    for ($ri = 1; $ri -le $count; $ri++) {
      $isFemalePro = (Get-Random -Minimum 0 -Maximum 2) -eq 0
      if ($isFemalePro) { $pfirst = Get-RandItem $femaleNames } else { $pfirst = Get-RandItem $maleNames }
      $plast = Get-RandItem $lastNames
      if (-not $pfirst -or -not $plast) { continue }

      $pFullName = "$pfirst $plast"
      $pEmail = New-ProfessionalEmail -role $role -firstName $pfirst -lastName $plast
      $pPhone = New-TunisPhone
      $pDob = (New-RandomBirthDate -minAge 22 -maxAge 65).ToString('yyyy-MM-dd')
      if ($isFemalePro) { $pGender = 'F' } else { $pGender = 'M' }

      $pool = $specByRole[$role]
      $pSpec = if ($pool) { Get-Random -InputObject $pool } else { 'General' }
      $pYearsExp = Get-Random -Minimum 1 -Maximum 26
      $pRadius = Get-Random -Minimum 2 -Maximum 26
      $pReviews = Get-Random -Minimum 0 -Maximum 401
      $pRating = [Math]::Round((Get-Random -Minimum 40 -Maximum 51) / 10.0, 1)
      $pIsOnline = (Get-Random -Minimum 0 -Maximum 100) -lt [int]($DoctorOnlineChance * 100)

      $createPro = Create-Professional -role $role -fullName $pFullName -email $pEmail -firstName $pfirst -lastName $plast -phone $pPhone -dob $pDob -gender $pGender -specialty $pSpec -yearsExp $pYearsExp -radius $pRadius -reviews $pReviews -rating $pRating -isOnline $pIsOnline
      if (-not $createPro.Ok -and $createPro.Status -ne 409) {
        $rawOneLine = (($createPro.Raw) -replace "\s+"," ")
        $failures.Add([pscustomobject]@{ Step='createProfessional'; PatientIndex=$ri; Details=("role=${role}; status=$($createPro.Status); " + $rawOneLine) }) | Out-Null
        Write-Host ("Create-Professional FAILED role=${role} status=$($createPro.Status)") -ForegroundColor Yellow
        if ($createPro.Raw) { Write-Host $createPro.Raw -ForegroundColor DarkYellow }
        continue
      }

      $professionalAccounts.Add([pscustomobject]@{ role=$role; name=$pFullName; email=$pEmail }) | Out-Null

      if ($role -eq 'NURSE') { $createdNurses++ }
      elseif ($role -eq 'KINE') { $createdKines++ }
      elseif ($role -eq 'PSYCHIATRIST') { $createdPsychiatrists++ }

      Write-Host ("Created ${role}: $pFullName") -ForegroundColor Cyan
    }
  }
}

for ($pi = 1; $pi -le $PatientsCount; $pi++) {
  $isFemale = (Get-Random -Minimum 0 -Maximum 2) -eq 0
  if ($isFemale) {
    $first = Get-RandItem $femaleNames
  } else {
    $first = Get-RandItem $maleNames
  }
  $last = Get-RandItem $lastNames

  if (-not $first -or -not $last) {
    $failures.Add([pscustomobject]@{ Step='pickName'; PatientIndex=$pi; Details='Empty name list' }) | Out-Null
    continue
  }

  $fullName = "$first $last"
  $email = New-PatientEmail -firstName $first -lastName $last
  $phone = New-TunisPhone
  $bloodGroup = New-RandomBloodGroup

  $dob = (New-RandomBirthDate -minAge 18 -maxAge 80).ToString('yyyy-MM-dd')
  if ($isFemale) { $gender = 'F' } else { $gender = 'M' }

  $baseMedicalInfo = @{}

  $create = Invoke-Api -Method POST -Path "/auth/dev/create-patient" -Body @{
    email=$email
    name=$fullName
    password=$PasswordPlain
    firstName=$first
    lastName=$last
    phoneNumber=$phone
    dateOfBirth=$dob
    gender=$gender
    bloodGroup=$bloodGroup
    medicalInfo=$baseMedicalInfo
  }
  if (-not $create.Ok -and $create.Status -ne 409) {
    $failures.Add([pscustomobject]@{ Step='createPatient'; PatientIndex=$pi; Details=($create.Raw -replace "\s+"," ") }) | Out-Null
    continue
  }

  $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$email; password=$PasswordPlain }
  $token = $null
  if ($login.Ok -and $login.Json) {
    $token = Get-AuthTokenFromLoginResponse $login.Json
  }
  if (-not $token) {
    $failures.Add([pscustomobject]@{ Step='login'; PatientIndex=$pi; Details=($login.Raw -replace "\s+"," ") }) | Out-Null
    continue
  }

  # resolve USER patientId
  $patients = Invoke-Api -Method GET -Path "/api/patients" -Token $token
  if (-not $patients.Ok -or -not $patients.Json) {
    $failures.Add([pscustomobject]@{ Step='patientsList'; PatientIndex=$pi; Details=($patients.Raw -replace "\s+"," ") }) | Out-Null
    continue
  }
  $patientsArr = @($patients.Json)
  $userRow = $patientsArr | Where-Object { $_.type -eq 'USER' } | Select-Object -First 1
  if (-not $userRow) { $userRow = $patientsArr | Select-Object -First 1 }
  $userPatientId = $userRow.patientId
  if (-not $userPatientId) {
    $failures.Add([pscustomobject]@{ Step='resolveUserPatientId'; PatientIndex=$pi; Details='Missing patientId' }) | Out-Null
    continue
  }

  $targetPro = Get-NextProfessionalNeedingGrants
  if ($targetPro -and $targetPro.email) {
    $ok = Grant-ProfessionalAccessToPatient -patientToken $token -patientId $userPatientId -professionalEmail $targetPro.email
    if ($ok) {
      if (-not $grantsPerProfessional.ContainsKey($targetPro.email)) { $grantsPerProfessional[$targetPro.email] = 0 }
      $grantsPerProfessional[$targetPro.email] = [int]$grantsPerProfessional[$targetPro.email] + 1
    }
  } else {
    Grant-RandomProfessionalAccessToPatient -patientToken $token -patientId $userPatientId
  }

  # patch medical passport for the user (store phone/dob/gender + maybe meds/chronic)
  # start by setting a base medicalInfo + bloodGroup; then maybe patch in meds/chronic
  $patchBase = Invoke-Api -Method PATCH -Path "/api/account/medical-passport" -Token $token -Body @{ bloodGroup = $bloodGroup; medicalInfo = $baseMedicalInfo }
  if (-not $patchBase.Ok) {
    $failures.Add([pscustomobject]@{ Step='patchMedicalPassportBase'; PatientIndex=$pi; Details=($patchBase.Raw -replace "\s+"," ") }) | Out-Null
  }

  Maybe-AddAllergies -token $token -patientId $userPatientId -allergiesCounter ([ref]$allergiesAdded)
  Maybe-AddChronicConditions -token $token -patientId $userPatientId
  Maybe-PatchMedicalPassport -token $token -path "/api/account/medical-passport" -medsCounter ([ref]$medsAdded)
  Add-PatientMedications -token $token -patientId $userPatientId -medsCounter ([ref]$medsAdded)

  $familyCount = 0

  $hasFamily = (Get-Random -Minimum 0 -Maximum 100) -lt [int]($FamilyChance * 100)
  if ($hasFamily) {
    $targetFamily = Get-Random -Minimum $MinFamilyMembers -Maximum ($MaxFamilyMembers + 1)

    for ($fi = 1; $fi -le $targetFamily; $fi++) {
      $relPool = @('SPOUSE','CHILD','PARENT','SIBLING')
      $rel = Get-Random -InputObject $relPool
      $fmFemale = (Get-Random -Minimum 0 -Maximum 2) -eq 0
      if ($fmFemale) {
        $fmFirst = Get-RandItem $femaleNames
      } else {
        $fmFirst = Get-RandItem $maleNames
      }
      $fmFull = "$fmFirst $last"

      $fmDob = (New-RandomBirthDate -minAge 1 -maxAge 90)
      if ($rel -eq 'CHILD') { $fmDob = (New-RandomBirthDate -minAge 1 -maxAge 25) }
      if ($rel -eq 'PARENT') { $fmDob = (New-RandomBirthDate -minAge 45 -maxAge 90) }

      if ($fmFemale) { $fmGender = 'F' } else { $fmGender = 'M' }

      $fmBloodGroup = New-RandomBloodGroup
      $fmBaseMedicalInfo = @{}

      $createFm = Invoke-Api -Method POST -Path "/api/family" -Token $token -Body @{ fullName=$fmFull; relationship=$rel; birthDate=$fmDob.ToString('yyyy-MM-dd'); gender=$fmGender; medicalInfo=$fmBaseMedicalInfo }
      if (-not $createFm.Ok -or -not $createFm.Json -or -not $createFm.Json.id) {
        continue
      }

      $fmId = $createFm.Json.id
      $createdFamily++
      $familyCount++

      # re-list patients to find the corresponding patientId for this family member
      $patients2 = Invoke-Api -Method GET -Path "/api/patients" -Token $token
      $fmPatientId = $null
      if ($patients2.Ok -and $patients2.Json) {
        $arr2 = @($patients2.Json)
        $row2 = $arr2 | Where-Object { $_.familyMemberId -eq $fmId } | Select-Object -First 1
        if ($row2 -and $row2.patientId) { $fmPatientId = $row2.patientId }
      }

      if ($fmPatientId) {
        Maybe-AddAllergies -token $token -patientId $fmPatientId -allergiesCounter ([ref]$allergiesAdded)
        Maybe-AddChronicConditions -token $token -patientId $fmPatientId
        Add-PatientMedications -token $token -patientId $fmPatientId -medsCounter ([ref]$medsAdded)
      }

      $fmPatchBase = Invoke-Api -Method PATCH -Path ("/api/family/$fmId/medical-passport") -Token $token -Body @{ bloodGroup = $fmBloodGroup; medicalInfo = $fmBaseMedicalInfo }
      if ($fmPatchBase.Ok) {
        Maybe-PatchMedicalPassport -token $token -path ("/api/family/$fmId/medical-passport") -medsCounter ([ref]$medsAdded)
      }
    }
  }

  $createdUsers++
  Write-Host ("Created Patient: $fullName + $familyCount family members") -ForegroundColor Green
}

Write-Host ""
Write-Host "================ SUMMARY ================" -ForegroundColor Cyan
Write-Host ("Doctors created:      $createdDoctors")
Write-Host ("Nurses created:       $createdNurses")
Write-Host ("Kines created:        $createdKines")
Write-Host ("Psychiatrists created:$createdPsychiatrists")
Write-Host ("Users created:        $createdUsers")
Write-Host ("Family members added: $createdFamily")
Write-Host ("Allergies added:      $allergiesAdded")
Write-Host ("Current meds added:   $medsAdded")

if ($professionalAccounts.Count -gt 0) {
  Write-Host ""
  Write-Host "================ TEST CREDENTIALS ================" -ForegroundColor Cyan
  Write-Host ("Password for all seeded professionals: $PasswordPlain") -ForegroundColor DarkGray
  $professionalAccounts | Sort-Object role,name | Select-Object role,name,email | Format-Table -AutoSize
}

if ($failures.Count -gt 0) {
  Write-Host "" 
  Write-Host ("Failures: $($failures.Count)") -ForegroundColor Yellow
  $failures | Select-Object -First 20 | Format-Table -AutoSize
  Write-Host "(showing first 20 failures)" -ForegroundColor DarkGray
}

exit 0
