package com.omnilinks.omnicare_backend.features.patient.controller;

import com.omnilinks.omnicare_backend.features.patient.dto.FamilyMemberRequest;
import com.omnilinks.omnicare_backend.features.patient.dto.FamilyMemberResponse;
import com.omnilinks.omnicare_backend.features.patient.service.PatientService;
import com.omnilinks.omnicare_backend.features.passport.dto.MedicalPassportRequest;
import com.omnilinks.omnicare_backend.features.passport.dto.MedicalPassportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/family")
    public ResponseEntity<List<FamilyMemberResponse>> getAllFamilyMembers(
            @RequestHeader("X-User-Id") UUID userId) {
        List<FamilyMemberResponse> members = patientService.getAllFamilyMembers(userId);
        return ResponseEntity.ok(members);
    }

    @PostMapping("/family")
    public ResponseEntity<FamilyMemberResponse> addFamilyMember(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody FamilyMemberRequest request) {
        FamilyMemberResponse response = patientService.addFamilyMember(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/passport/{memberId}")
    public ResponseEntity<MedicalPassportResponse> getMedicalPassport(
            @PathVariable UUID memberId,
            @RequestHeader("X-User-Id") UUID userId) {
        MedicalPassportResponse response = patientService.getMedicalPassport(memberId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/passport/{memberId}")
    public ResponseEntity<MedicalPassportResponse> updateMedicalPassport(
            @PathVariable UUID memberId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody MedicalPassportRequest request) {
        MedicalPassportResponse response = patientService.updateMedicalPassport(memberId, userId, request);
        return ResponseEntity.ok(response);
    }
}
