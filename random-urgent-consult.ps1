param(
  [string]$DoctorEmail,
  [string]$DoctorPassword,
  [string]$PatientEmail,
  [string]$PatientPassword,
  [string]$BaseUrl = "http://localhost:8080",
  [int]$AffectedAreasCountMax = 2
)

$ErrorActionPreference = "Stop"

if ($BaseUrl) {
  $BaseUrl = $BaseUrl.Trim()
  while ($BaseUrl.EndsWith("/")) { $BaseUrl = $BaseUrl.Substring(0, $BaseUrl.Length - 1) }
  if ($BaseUrl.EndsWith("/api")) { $BaseUrl = $BaseUrl.Substring(0, $BaseUrl.Length - 4) }
}

function Get-DevJson($path) {
  return Invoke-RestMethod -Method Get -Uri ("$BaseUrl" + $path) -Headers @{ Accept = "application/json" }
}

function Try-GetDevJson($path) {
  try {
    return Get-DevJson $path
  }
  catch {
    $response = $_.Exception.Response
    if ($response -and [int]$response.StatusCode -eq 404) {
      return $null
    }
    throw
  }
}

$r = [System.Random]::new()

if (-not $DoctorEmail -or $DoctorEmail.Trim().Length -eq 0) {
  $active = Try-GetDevJson "/api/auth/dev/active-doctor"
  if ($active -and $active.email) { $DoctorEmail = $active.email }
  elseif ($active -and $active.data -and $active.data.email) { $DoctorEmail = $active.data.email }
  if (-not $DoctorPassword -or $DoctorPassword.Trim().Length -eq 0) {
    if ($active -and $active.password) { $DoctorPassword = $active.password }
    elseif ($active -and $active.data -and $active.data.password) { $DoctorPassword = $active.data.password }
  }

  if (-not $DoctorEmail -or $DoctorEmail.Trim().Length -eq 0) {
    $accounts = Get-DevJson "/api/auth/dev/accounts"
    $list = @()
    if ($accounts -is [System.Collections.IEnumerable] -and -not ($accounts -is [string])) {
      $list = @($accounts)
    }
    elseif ($accounts -and $accounts.data) {
      $list = @($accounts.data)
    }

    $doctors = @($list | Where-Object { $_ -and $_.role -and $_.email -and ($_.role.ToString().ToUpperInvariant() -eq "DOCTOR") })
    if ($doctors.Count -eq 0) { throw "No DOCTOR accounts found at /api/auth/dev/accounts" }
    $DoctorEmail = $doctors[$r.Next(0, $doctors.Count)].email
  }
}

if (-not $DoctorPassword -or $DoctorPassword.Trim().Length -eq 0) { $DoctorPassword = "Passw0rd!123" }

if (-not $PatientEmail -or $PatientEmail.Trim().Length -eq 0) {
  $accounts = Get-DevJson "/api/auth/dev/accounts"
  $list = @()
  if ($accounts -is [System.Collections.IEnumerable] -and -not ($accounts -is [string])) {
    $list = @($accounts)
  }
  elseif ($accounts -and $accounts.data) {
    $list = @($accounts.data)
  }

  $patients = @($list | Where-Object { $_ -and $_.role -and $_.email -and ($_.role.ToString().ToUpperInvariant() -eq "PATIENT") })
  if ($patients.Count -eq 0) { throw "No PATIENT accounts found at /api/auth/dev/accounts" }
  $PatientEmail = $patients[$r.Next(0, $patients.Count)].email
}

if (-not $PatientPassword -or $PatientPassword.Trim().Length -eq 0) { $PatientPassword = "Passw0rd!123" }

$doctorLoginBody = @{ email = $DoctorEmail; password = $DoctorPassword } | ConvertTo-Json -Compress
$doctorLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $doctorLoginBody

$doctorToken = $null
if ($doctorLogin -and $doctorLogin.data -and $doctorLogin.data.accessToken) { $doctorToken = $doctorLogin.data.accessToken }
if (-not $doctorToken -and $doctorLogin -and $doctorLogin.accessToken) { $doctorToken = $doctorLogin.accessToken }
if (-not $doctorToken) { throw "Could not login as doctor or token missing in response." }

$doctorHeaders = @{ Authorization = "Bearer $doctorToken"; Accept = "application/json" }
$providerMe = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/providers/me" -Headers $doctorHeaders
$providerId = $null
if ($providerMe -and $providerMe.providerId) { $providerId = $providerMe.providerId }
elseif ($providerMe -and $providerMe.data -and $providerMe.data.providerId) { $providerId = $providerMe.data.providerId }
if (-not $providerId) { throw "Could not resolve providerId from /api/providers/me for doctor=$DoctorEmail" }

$patientLoginBody = @{ email = $PatientEmail; password = $PatientPassword } | ConvertTo-Json -Compress
$patientLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $patientLoginBody

$patientToken = $null
if ($patientLogin -and $patientLogin.data -and $patientLogin.data.accessToken) { $patientToken = $patientLogin.data.accessToken }
if (-not $patientToken -and $patientLogin -and $patientLogin.accessToken) { $patientToken = $patientLogin.accessToken }
if (-not $patientToken) { throw "Could not login as patient or token missing in response." }

$headers = @{ Authorization = "Bearer $patientToken"; Accept = "application/json" }

$patientsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/patients" -Headers $headers
$patients = $null
if ($patientsResp -is [System.Collections.IEnumerable] -and -not ($patientsResp -is [string])) {
  $patients = @($patientsResp)
}
elseif ($patientsResp -and $patientsResp.data) {
  $patients = @($patientsResp.data)
}
else {
  $patients = @()
}

if (-not $patients -or $patients.Count -eq 0) { throw "No patients returned from /api/patients for this account." }

$selfPatients = @($patients | Where-Object { $_ -and $_.relationship -and ($_.relationship.ToString().ToUpperInvariant() -eq "SELF") })
if ($selfPatients.Count -gt 0) {
  $patientRow = $selfPatients[$r.Next(0, $selfPatients.Count)]
}
else {
  $patientRow = $patients[$r.Next(0, $patients.Count)]
}

$patientId = $null
if ($patientRow.patientId) { $patientId = $patientRow.patientId }
elseif ($patientRow.id) { $patientId = $patientRow.id }
if (-not $patientId) { throw "Could not find patientId in patient row." }

$patientName = $null
if ($patientRow.displayName) { $patientName = $patientRow.displayName }
elseif ($patientRow.name) { $patientName = $patientRow.name }

$affectedAreasKeys = @(
  "front-head",
  "front-neck",
  "back-neck",
  "front-chest-pectoral-left",
  "front-chest-pectoral-right",
  "front-abs-upper",
  "front-abs-middle",
  "front-abs-lower",
  "front-oblique-left",
  "front-oblique-right",
  "front-serratus-left",
  "front-serratus-right",
  "back-trap-upper",
  "back-lat-left",
  "back-lat-right",
  "back-spine-upper",
  "back-spine-middle",
  "back-lumbar",
  "back-glute-left",
  "back-glute-right",
  "front-shoulder-deltoid-left",
  "front-shoulder-deltoid-right",
  "back-shoulder-deltoid-left",
  "back-shoulder-deltoid-right",
  "front-bicep-left",
  "front-bicep-right",
  "back-tricep-left",
  "back-tricep-right",
  "front-forearm-left",
  "front-forearm-right",
  "back-forearm-left",
  "back-forearm-right",
  "front-hand-left",
  "front-hand-right",
  "back-hand-left",
  "back-hand-right",
  "front-thigh-quad-left",
  "front-thigh-quad-right",
  "back-thigh-hamstring-left",
  "back-thigh-hamstring-right",
  "front-knee-left",
  "front-knee-right",
  "back-knee-left",
  "back-knee-right",
  "front-shin-left",
  "front-shin-right",
  "front-calf-left",
  "front-calf-right",
  "back-calf-left",
  "back-calf-right",
  "front-foot-left",
  "front-foot-right",
  "back-foot-left",
  "back-foot-right"
)

$painLevel = 7 + $r.Next(0, 4)
$symptomsPool = @(
  "URGENT: Severe chest pain and shortness of breath",
  "URGENT: High fever and persistent vomiting",
  "URGENT: Possible allergic reaction; facial swelling",
  "URGENT: Sudden intense migraine and blurred vision",
  "URGENT: Severe abdominal pain with nausea",
  "URGENT: Dizziness and fainting episodes",
  "URGENT: Severe back pain radiating to leg"
)
$symptoms = $symptomsPool[$r.Next(0, $symptomsPool.Count)]

$areasCount = 0
if ($AffectedAreasCountMax -le 0) { $areasCount = 0 } else { $areasCount = 1 + $r.Next(0, $AffectedAreasCountMax) }
$affectedAreas = @()
for ($i = 0; $i -lt $areasCount; $i++) {
  $pick = $affectedAreasKeys[$r.Next(0, $affectedAreasKeys.Count)]
  if (-not ($affectedAreas -contains $pick)) { $affectedAreas += $pick }
}

$consultBodyObj = @{
  symptoms      = $symptoms
  providerId    = $providerId
  patientId     = $patientId
  latitude      = 36.8065
  longitude     = 10.1815
  city          = "Tunis"
  streetAddress = "Avenue Habib Bourguiba"
  painLevel     = $painLevel
  locationType  = "HOME"
}
if ($affectedAreas.Count -gt 0) {
  $consultBodyObj.affectedAreas = $affectedAreas
}

$consultBody = $consultBodyObj | ConvertTo-Json -Compress

Write-Host ("Using patientId=" + $patientId + $(if ($patientName) { " name=" + $patientName } else { "" }))
Write-Host ("Using providerId=" + $providerId + " for doctor=" + $DoctorEmail)
Write-Host ("Posting consultation: painLevel=" + $painLevel + " affectedAreas=" + ($affectedAreas -join ","))

$newConsult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/consultations" -Headers $headers -ContentType "application/json" -Body $consultBody

$cid = $null
$cstatus = $null
if ($newConsult.id) { $cid = $newConsult.id }
elseif ($newConsult.data -and $newConsult.data.id) { $cid = $newConsult.data.id }
if ($newConsult.status) { $cstatus = $newConsult.status }
elseif ($newConsult.data -and $newConsult.data.status) { $cstatus = $newConsult.data.status }

Write-Host ("Created consultation id=" + $cid + " status=" + $cstatus)
$newConsult | ConvertTo-Json -Depth 20
