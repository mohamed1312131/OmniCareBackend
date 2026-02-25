param(
  [string]$BaseUrl = "http://localhost:8080",
  [securestring]$Password = (ConvertTo-SecureString "Passw0rd!123" -AsPlainText -Force)
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

function New-TestEmail([string]$prefix) {
  $stamp = Get-Date -Format "yyyyMMddHHmmss"
  return "$prefix+$stamp@dev.local"
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

function As-Array($v) {
  return @($v)
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

if ($BaseUrl -notmatch '^https?://') {
  throw "Invalid BaseUrl '$BaseUrl'. Usage: .\\test-consultation-flow.ps1 [-BaseUrl http://localhost:8080]"
}

$PasswordPlain = ConvertFrom-SecureStringPlain $Password

Write-Host "BaseUrl: $BaseUrl"

# ------------------------------------------------------------
# Setup: Create + Login Patient (Sarah) and Doctor (Dr. Ahmed)
# ------------------------------------------------------------
$patientEmail = New-TestEmail "sarah"
$doctorEmail = New-TestEmail "ahmed.doctor"

Print-Step "SETUP: Create verified Patient (Sarah)"
$createPatient = Invoke-Api -Method POST -Path "/auth/dev/create-patient" -Body @{ email=$patientEmail; name="Sarah"; password=$PasswordPlain }
Print-Json $createPatient.Json
if (-not $createPatient.Ok -and $createPatient.Status -ne 409) { throw "create-patient failed: $($createPatient.Status) $($createPatient.Raw)" }

Print-Step "SETUP: Create verified Doctor (Dr. Ahmed)"
$createDoctor = Invoke-Api -Method POST -Path "/auth/dev/create-doctor" -Body @{ email=$doctorEmail; name="Dr. Ahmed"; password=$PasswordPlain }
Print-Json $createDoctor.Json
if (-not $createDoctor.Ok -and $createDoctor.Status -ne 409) { throw "create-doctor failed: $($createDoctor.Status) $($createDoctor.Raw)" }

Print-Step "SETUP: Login as Sarah"
$loginPatient = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$patientEmail; password=$PasswordPlain }
Print-Json $loginPatient.Json
if (-not $loginPatient.Ok -or -not $loginPatient.Json -or -not $loginPatient.Json.token) { throw "patient login failed: $($loginPatient.Status) $($loginPatient.Raw)" }
$patientToken = $loginPatient.Json.token

Print-Step "SETUP: Login as Dr. Ahmed"
$loginDoctor = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email=$doctorEmail; password=$PasswordPlain }
Print-Json $loginDoctor.Json
if (-not $loginDoctor.Ok -or -not $loginDoctor.Json -or -not $loginDoctor.Json.token) { throw "doctor login failed: $($loginDoctor.Status) $($loginDoctor.Raw)" }
$doctorToken = $loginDoctor.Json.token

Print-Step "SETUP: Resolve authenticated doctor's doctorId"
$docProfile = Invoke-Api -Method GET -Path "/api/doctor/profile" -Token $doctorToken
Print-Json $docProfile.Json
if (-not $docProfile.Ok -or -not $docProfile.Json -or -not $docProfile.Json.doctorId) { throw "doctor profile failed: $($docProfile.Status) $($docProfile.Raw)" }
$doctorId = $docProfile.Json.doctorId
Write-Host "Resolved authenticated doctorId: $doctorId" -ForegroundColor Green

# ------------------------------------------------------------
# Patient Side
# ------------------------------------------------------------
Print-Step "PATIENT: Search medication catalog (Paracetamol)"
$medSearch = Invoke-Api -Method GET -Path "/api/medications/search?q=Paracetamol" -Token $patientToken
Print-Json $medSearch.Json
if (-not $medSearch.Ok) { throw "medication search failed: $($medSearch.Status) $($medSearch.Raw)" }

Print-Step "PATIENT: List patients (get Sarah patientId)"
$patients = Invoke-Api -Method GET -Path "/api/patients" -Token $patientToken
Print-Json $patients.Json
if (-not $patients.Ok -or -not $patients.Json) { throw "patients list failed: $($patients.Status) $($patients.Raw)" }

# pick the USER patient (Sarah)
$patientsArr = @($patients.Json)
Write-Host "Resolved patients count: $($patientsArr.Length)" -ForegroundColor DarkGray
if ($patientsArr.Length -lt 1) { throw "patients list empty: $($patients.Status) $($patients.Raw)" }

$patientRow = $patientsArr | Where-Object { $_.type -eq 'USER' } | Select-Object -First 1
if ($null -eq $patientRow) { $patientRow = $patientsArr | Select-Object -First 1 }
$sarahPatientId = $patientRow.patientId
if (-not $sarahPatientId) { throw "Could not resolve Sarah patientId from /api/patients" }
Write-Host "Resolved sarahPatientId: $sarahPatientId" -ForegroundColor Green

Print-Step "PATIENT: Add allergy PENICILLINE to Sarah (chronicConditions/allergies)"
$addAllergy = Invoke-Api -Method POST -Path "/api/patients/$sarahPatientId/allergies" -Token $patientToken -Body @{ substance="PENICILLINE"; reaction=""; severity="HIGH" }
Print-Json $addAllergy.Json
if (-not $addAllergy.Ok -and $addAllergy.Status -ne 409) { throw "add allergy failed: $($addAllergy.Status) $($addAllergy.Raw)" }

Print-Step "PATIENT: List allergies to confirm"
$listAllergies = Invoke-Api -Method GET -Path "/api/patients/$sarahPatientId/allergies" -Token $patientToken
Print-Json $listAllergies.Json
if (-not $listAllergies.Ok) { throw "list allergies failed: $($listAllergies.Status) $($listAllergies.Raw)" }

# ------------------------------------------------------------
# Interaction: Create consultation (PENDING)
# ------------------------------------------------------------
Print-Step "INTERACTION: Doctor search (get a doctorId to target)"
$docList = Invoke-Api -Method GET -Path "/api/doctors" -Token $patientToken
Print-Json $docList.Json
if (-not $docList.Ok -or -not $docList.Json) { throw "doctor list failed: $($docList.Status) $($docList.Raw)" }

$doctorsArr = @($docList.Json)
Write-Host "Resolved doctors count: $($doctorsArr.Length)" -ForegroundColor DarkGray
if ($doctorsArr.Length -lt 1) { throw "doctor list empty: $($docList.Status) $($docList.Raw)" }

Write-Host "Using authenticated doctorId for targeting: $doctorId" -ForegroundColor DarkGray

Print-Step "INTERACTION: Create Consultation (PENDING, symptoms=High Fever, fee=100 TND)"
$createConsult = Invoke-Api -Method POST -Path "/api/consultations" -Token $patientToken -Body @{ doctorId=$doctorId; symptoms="High Fever"; fee=100 }
Print-Json $createConsult.Json
if (-not $createConsult.Ok -or -not $createConsult.Json -or -not $createConsult.Json.id) { throw "create consultation failed: $($createConsult.Status) $($createConsult.Raw)" }
$consultationId = $createConsult.Json.id
Write-Host "Created consultationId: $consultationId" -ForegroundColor Green

Print-Step "INTERACTION: Fetch Consultation by ID (patient view)"
$getConsultPatient = Invoke-Api -Method GET -Path "/api/consultations/$consultationId" -Token $patientToken
Print-Json $getConsultPatient.Json
if (-not $getConsultPatient.Ok) { throw "get consultation as patient failed: $($getConsultPatient.Status) $($getConsultPatient.Raw)" }

# ------------------------------------------------------------
# Doctor Side: complete + diagnosis + treatment
# ------------------------------------------------------------
Print-Step "DOCTOR: Update Consultation → COMPLETED + diagnosis + treatment"
$patch = Invoke-Api -Method POST -Path "/api/consultations/$consultationId/complete" -Token $doctorToken -Body @{ diagnosis="Viral Infection"; treatment="Amoxicilline" }
Print-Json $patch.Json
if (-not $patch.Ok) { throw "patch consultation failed: $($patch.Status) $($patch.Raw)" }

if ($patch.Json -and $patch.Json.allergyWarning) {
  Write-Host "Allergy Warning Detected: $($patch.Json.allergyWarning)" -ForegroundColor Yellow
} else {
  Write-Host "No allergy warning returned. If you expected one, verify Sarah has PENICILLINE allergy and restart the backend." -ForegroundColor Yellow
}

Print-Step "DOCTOR: Fetch Consultation by ID (doctor view)"
$getConsultDoctor = Invoke-Api -Method GET -Path "/api/consultations/$consultationId" -Token $doctorToken
Print-Json $getConsultDoctor.Json
if (-not $getConsultDoctor.Ok) { throw "get consultation as doctor failed: $($getConsultDoctor.Status) $($getConsultDoctor.Raw)" }

# ------------------------------------------------------------
# Financial Check
# ------------------------------------------------------------
Print-Step "FINANCIAL CHECK: GET /api/doctor/revenue (expect 15 TND fee + 85 TND net)"
$rev = Invoke-Api -Method GET -Path "/api/doctor/revenue?limit=10" -Token $doctorToken
Print-Json $rev.Json
if (-not $rev.Ok) { throw "doctor revenue failed: $($rev.Status) $($rev.Raw)" }

Write-Host "" 
Write-Host "Expected for 100.00 TND: omnicareFee=15.00, netAmount=85.00" -ForegroundColor Green
Write-Host "Look for your consultationId in recentTransactions." -ForegroundColor Green

# ------------------------------------------------------------
# DBeaver SQL queries
# ------------------------------------------------------------
Print-Step "DBEAVER: SQL you can run to verify consultations + doctors"

$sql = @()
$sql += "-- 1) Verify the consultation row"
$sql += "SELECT id, doctor_id, patient_id, patient_user_id, patient_family_member_id, status, symptoms, diagnosis, treatment, fee, payment_method, duration_minutes, timestamp"
$sql += "FROM consultations"
$sql += "WHERE id = '$consultationId';"
$sql += ""
$sql += "-- 2) Verify the doctor profile exists and is linked to the doctor user"
$sql += "SELECT d.id AS doctor_id, d.user_id, u.email, u.name, u.role, d.specialty, d.is_online"
$sql += "FROM doctors d"
$sql += "JOIN users u ON u.id = d.user_id"
$sql += "WHERE u.email = '$doctorEmail';"
$sql += ""
$sql += "-- 3) Verify Sarah user + patient row"
$sql += "SELECT u.id AS user_id, u.email, u.name, u.role, p.id AS patient_id, p.type, p.owner_user_id"
$sql += "FROM users u"
$sql += "JOIN patients p ON p.user_id = u.id"
$sql += "WHERE u.email = '$patientEmail';"
$sql += ""
$sql += "-- 4) Verify allergy row"
$sql += "SELECT id, patient_id, substance, reaction, severity, recorded_at"
$sql += "FROM patient_allergies"
$sql += "WHERE patient_id = '$sarahPatientId'"
$sql += "ORDER BY recorded_at DESC;"

($sql -join "\n") | Write-Host

Print-Step "DONE"
Write-Host "Consultation flow completed. If any step failed, paste the step output here." -ForegroundColor Green
