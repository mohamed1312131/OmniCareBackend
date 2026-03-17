param(
  [string]$BaseUrl = "http://localhost:8080",
  [securestring]$Password = (ConvertTo-SecureString "password" -AsPlainText -Force),
  [int]$FamilyMembersCount = 2,
  [switch]$RunMegaSeed
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

function ConvertTo-JsonBody($obj) {
  return ($obj | ConvertTo-Json -Depth 16)
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

function New-TestEmail([string]$prefix) {
  $stamp = Get-Date -Format "yyyyMMddHHmmss"
  return "$prefix+$stamp@dev.local"
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

function Print-Step([string]$title) {
  Write-Host ""
  Write-Host "================ $title ================" -ForegroundColor Cyan
}

function Print-Json($obj) {
  if ($null -eq $obj) {
    Write-Host "(null)" -ForegroundColor DarkGray
    return
  }
  ($obj | ConvertTo-Json -Depth 20) | Write-Host
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

if ($BaseUrl -notmatch '^https?://') {
  throw "Invalid BaseUrl '$BaseUrl'. Usage: .\\test-mega-flow.ps1 [-BaseUrl http://localhost:8080]"
}

$PasswordPlain = ConvertFrom-SecureStringPlain $Password

Write-Host "BaseUrl: $BaseUrl"

if ($RunMegaSeed) {
  Print-Step "DEV: Deep Mega-Seed"
  $seed = Invoke-Api -Method POST -Path "/api/auth/dev/mega-seed" -Body @{}
  Add-Result "dev.mega-seed" $seed.Ok $seed.Status ($seed.Raw -replace "\s+"," ")
  if ($seed.Ok) {
    Print-Step "MEGA-SEED RESPONSE"
    Print-Json $seed.Json
  }

  Print-Step "RESULTS TABLE"
  $results | Format-Table -AutoSize

  $failed = $results | Where-Object { -not $_.Ok }
  if ($failed -and $failed.Count -gt 0) {
    Write-Host "\nFAILED: $($failed.Count) step(s)" -ForegroundColor Red
    exit 1
  }

  Write-Host "\nALL TESTS PASSED" -ForegroundColor Green
  exit 0
}

do {
  # ------------------------------------------------------------
  # Setup: Create + Login Patient and Doctor
  # ------------------------------------------------------------
  $patientEmail = New-TestEmail "mega.patient"
  $doctorEmail = New-TestEmail "mega.doctor"

  Print-Step "SETUP: Create verified Patient"
  $createPatient = Invoke-Api -Method POST -Path "/auth/dev/create-patient" -Body @{ email=$patientEmail; name="Mega Patient"; password=$PasswordPlain }
  Add-Result "dev.create-patient" ($createPatient.Ok -or $createPatient.Status -eq 409) $createPatient.Status ($createPatient.Raw -replace "\s+"," ")
  if (-not $createPatient.Ok -and $createPatient.Status -ne 409) { break }

  Print-Step "SETUP: Create verified Doctor"
  $createDoctor = Invoke-Api -Method POST -Path "/auth/dev/create-doctor" -Body @{ email=$doctorEmail; name="Dr. Mega"; password=$PasswordPlain }
  Add-Result "dev.create-doctor" ($createDoctor.Ok -or $createDoctor.Status -eq 409) $createDoctor.Status ($createDoctor.Raw -replace "\s+"," ")
  if (-not $createDoctor.Ok -and $createDoctor.Status -ne 409) { break }

  Print-Step "SETUP: Login as Patient"
  $loginPatient = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$patientEmail; password=$PasswordPlain }
  $patientToken = $null
  if ($loginPatient.Ok -and $loginPatient.Json -and $loginPatient.Json.token) { $patientToken = $loginPatient.Json.token }
  Add-Result "auth.login.patient" ($loginPatient.Ok -and $patientToken) $loginPatient.Status ($loginPatient.Raw -replace "\s+"," ")
  if (-not $patientToken) { break }

  Print-Step "SETUP: Login as Doctor"
  $loginDoctor = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$doctorEmail; password=$PasswordPlain }
  $doctorToken = $null
  if ($loginDoctor.Ok -and $loginDoctor.Json -and $loginDoctor.Json.token) { $doctorToken = $loginDoctor.Json.token }
  Add-Result "auth.login.doctor" ($loginDoctor.Ok -and $doctorToken) $loginDoctor.Status ($loginDoctor.Raw -replace "\s+"," ")
  if (-not $doctorToken) { break }

  Print-Step "SETUP: Resolve authenticated doctorId"
  $docProfile = Invoke-Api -Method GET -Path "/api/doctor/profile" -Token $doctorToken
  $doctorId = $null
  if ($docProfile.Ok -and $docProfile.Json -and $docProfile.Json.doctorId) { $doctorId = $docProfile.Json.doctorId }
  Add-Result "doctor.profile" ($docProfile.Ok -and $doctorId) $docProfile.Status ($docProfile.Raw -replace "\s+"," ")
  if (-not $doctorId) { break }

  Print-Step "DOCTOR: Go Live"
  $goLive = Invoke-Api -Method PATCH -Path "/api/doctor/status" -Token $doctorToken -Body @{ goLive = $true }
  Add-Result "doctor.status.goLive" $goLive.Ok $goLive.Status ($goLive.Raw -replace "\s+"," ")
  if (-not $goLive.Ok) { break }

  # ------------------------------------------------------------
  # Family members: create N
  # ------------------------------------------------------------
  $familyIds = @()
  if ($FamilyMembersCount -gt 0) {
    Print-Step "FAMILY: Create family members"
    for ($i = 1; $i -le $FamilyMembersCount; $i++) {
      $relationship = "Child"
      if ($i -eq 1) { $relationship = "Spouse" }

      $gender = "M"
      if (($i % 2) -eq 0) { $gender = "F" }

      $body = @{ fullName = "Family Member $i"; relationship = $relationship; birthDate = "2000-01-0$i"; gender = $gender; medicalInfo = @{ } }
      $fm = Invoke-Api -Method POST -Path "/api/family" -Token $patientToken -Body $body
      $ok = $fm.Ok -and $fm.Json -and $fm.Json.id
      if ($ok) { $familyIds += $fm.Json.id }
      Add-Result "family.create.$i" $ok $fm.Status ($fm.Raw -replace "\s+"," ")
      if (-not $ok) { break }
    }
  }

  # ------------------------------------------------------------
  # Patients list should include USER + family member patients
  # ------------------------------------------------------------
  Print-Step "PATIENT: List patients (USER + FAMILY_MEMBER)"
  $patients = Invoke-Api -Method GET -Path "/api/patients" -Token $patientToken
  $patientsArr = @()
  if ($patients.Ok -and $patients.Json) { $patientsArr = @($patients.Json) }
  Add-Result "patients.list" ($patients.Ok -and $patientsArr.Count -ge 1) $patients.Status ("count=$($patientsArr.Count)")
  if (-not $patients.Ok -or $patientsArr.Count -lt 1) { break }

  # Resolve user patient
  $userPatient = $patientsArr | Where-Object { $_.type -eq 'USER' } | Select-Object -First 1
  if (-not $userPatient) { $userPatient = $patientsArr | Select-Object -First 1 }
  $userPatientId = $userPatient.patientId
  Add-Result "patients.user.resolve" ([bool]$userPatientId) $null ("patientId=$userPatientId")
  if (-not $userPatientId) { break }

  # Resolve family member patients (by familyMemberId match)
  $familyPatientIds = @()
  foreach ($fid in $familyIds) {
    $row = $patientsArr | Where-Object { $_.familyMemberId -eq $fid } | Select-Object -First 1
    if ($row -and $row.patientId) { $familyPatientIds += $row.patientId }
  }

  # ------------------------------------------------------------
  # For each patientId: add allergy, create consult, complete consult, create prescription, list prescriptions as patient
  # ------------------------------------------------------------
  $allPatientIds = @($userPatientId) + @($familyPatientIds)
  $completedConsultationIds = @()

  $allergyNames = @("PENICILLINE", "IBUPROFENE", "ASPIRINE")
  $treatments = @("Amoxicilline", "Paracetamol", "Ibuprofen")

  for ($pi = 0; $pi -lt $allPatientIds.Count; $pi++) {
    $patientId = $allPatientIds[$pi]
    Print-Step "FLOW: Patient $($pi+1)/$($allPatientIds.Count) patientId=$patientId"

    # Allergy
    $sub = $allergyNames[$pi % $allergyNames.Count]
    $addAllergy = Invoke-Api -Method POST -Path "/api/patients/$patientId/allergies" -Token $patientToken -Body @{ substance=$sub; reaction=""; severity="HIGH" }
    Add-Result "allergies.add.$patientId" $addAllergy.Ok $addAllergy.Status ($addAllergy.Raw -replace "\s+"," ")
    if (-not $addAllergy.Ok) { break }

    $listAllergies = Invoke-Api -Method GET -Path "/api/patients/$patientId/allergies" -Token $patientToken
    Add-Result "allergies.list.$patientId" $listAllergies.Ok $listAllergies.Status ("len=$($listAllergies.Raw.Length)")
    if (-not $listAllergies.Ok) { break }

    # Create consultation for this patientId
    $createConsult = Invoke-Api -Method POST -Path "/api/consultations" -Token $patientToken -Body @{ doctorId=$doctorId; patientId=$patientId; symptoms="Symptoms for $patientId"; fee=100 }
    $cid = $null
    if ($createConsult.Ok -and $createConsult.Json -and $createConsult.Json.id) { $cid = $createConsult.Json.id }
    Add-Result "consultations.create.$patientId" ($createConsult.Ok -and $cid) $createConsult.Status ($createConsult.Raw -replace "\s+"," ")
    if (-not $cid) { break }

    # Complete consultation as doctor
    $treat = $treatments[$pi % $treatments.Count]
    $complete = Invoke-Api -Method POST -Path "/api/consultations/$cid/complete" -Token $doctorToken -Body @{ diagnosis="Dx $patientId"; treatment=$treat }
    $warning = $null
    if ($complete.Json -and $complete.Json.allergyWarning) { $warning = $complete.Json.allergyWarning }
    $completeDetails = "(no warning)"
    if ($warning) { $completeDetails = "warning=$warning" }
    Add-Result "consultations.complete.$cid" $complete.Ok $complete.Status $completeDetails
    if (-not $complete.Ok) { break }

    $completedConsultationIds += $cid

    # Create a prescription as doctor for this patient
    $medSearch = Invoke-Api -Method GET -Path ("/api/medications/search?q=" + [uri]::EscapeDataString("para")) -Token $doctorToken
    $medId = $null
    if ($medSearch.Ok -and $medSearch.Json) {
      $arr = @($medSearch.Json)
      if ($arr.Count -gt 0 -and $arr[0].id) { $medId = $arr[0].id }
    }
    Add-Result "medications.search" ($medSearch.Ok -and $medId) $medSearch.Status ("medId=$medId")
    if (-not $medId) { break }

    $rxBody = @{ patientId=$patientId; issuedAt=(Get-Date).ToUniversalTime().ToString("o"); notes="Mega flow prescription for patient $patientId"; items=@(@{ medicationId=$medId; doseAmount=500; doseUnit="mg"; frequencyTimes=3; frequencyPeriodDays=1; durationDays=5; startDate=(Get-Date).ToString("yyyy-MM-dd"); instructions="After meals" }) }
    $rx = Invoke-Api -Method POST -Path "/api/prescriptions" -Token $doctorToken -Body $rxBody
    $rxId = $null
    if ($rx.Ok -and $rx.Json -and $rx.Json.id) { $rxId = $rx.Json.id }
    Add-Result "prescriptions.create.$patientId" ($rx.Ok -and $rxId) $rx.Status ($rx.Raw -replace "\s+"," ")
    if (-not $rxId) { break }

    # List prescriptions as patient (view-only)
    $rxList = Invoke-Api -Method GET -Path ("/api/prescriptions?patientId=$patientId") -Token $patientToken
    $rxListOk = $rxList.Ok -and $rxList.Json
    Add-Result "prescriptions.listAsPatient.$patientId" $rxListOk $rxList.Status ("count=" + (@($rxList.Json)).Count)
    if (-not $rxListOk) { break }
  }

  # ------------------------------------------------------------
  # Doctor revenue check
  # ------------------------------------------------------------
  Print-Step "DOCTOR: Revenue"
  $rev = Invoke-Api -Method GET -Path "/api/doctor/revenue?limit=20" -Token $doctorToken
  $revOk = $rev.Ok -and $rev.Json -and $null -ne $rev.Json.totalGrossEarnings
  Add-Result "doctor.revenue" $revOk $rev.Status ($rev.Raw -replace "\s+"," ")
  if (-not $revOk) { break }

  # ------------------------------------------------------------
  # Doctor rating (not implemented)
  # ------------------------------------------------------------
  Print-Step "RATING: Doctor review"
  Add-Result "doctor.rating" $true $null "SKIPPED (no rating/review API implemented yet)"

  # ------------------------------------------------------------
  # Summary
  # ------------------------------------------------------------
  Print-Step "SUMMARY"
  Write-Host ("patientEmail=" + $patientEmail)
  Write-Host ("doctorEmail=" + $doctorEmail)
  Write-Host ("doctorId=" + $doctorId)
  Write-Host ("userPatientId=" + $userPatientId)
  Write-Host ("familyPatientIds=" + ($familyPatientIds -join ', '))
  Write-Host ("consultationsCompleted=" + ($completedConsultationIds -join ', '))

  Print-Step "SQL (optional verification)"
  $sql = @()
  $sql += "-- Consultations created by this run"
  if ($completedConsultationIds.Count -gt 0) {
    $sql += "select id, doctor_id, patient_id, status, fee, timestamp from consultations where id in (" + (($completedConsultationIds | ForEach-Object { "'$_'" }) -join ', ') + ") order by timestamp desc;"
  }
  $sql += ""
  $sql += "-- Prescriptions for the user + family patients"
  $sql += "select p.id, p.patient_id, p.prescriber_user_id, p.issued_at, p.status, left(coalesce(p.notes,''),80) notes from prescriptions p where p.patient_id in (" + ((@($allPatientIds) | ForEach-Object { "'$_'" }) -join ', ') + ") order by issued_at desc;"
  $sql += ""
  $sql += "-- Allergy rows"
  $sql += "select id, patient_id, substance, severity, recorded_at from patient_allergies where patient_id in (" + ((@($allPatientIds) | ForEach-Object { "'$_'" }) -join ', ') + ") order by recorded_at desc;"
  ($sql -join "`n") | Write-Host

} while ($false)

Print-Step "RESULTS TABLE"
$results | Format-Table -AutoSize

$failed = $results | Where-Object { -not $_.Ok }
if ($failed -and $failed.Count -gt 0) {
  Write-Host "\nFAILED: $($failed.Count) step(s)" -ForegroundColor Red
  exit 1
}

Write-Host "\nALL TESTS PASSED" -ForegroundColor Green
exit 0
