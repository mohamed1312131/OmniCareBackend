package com.omnilinks.omnicare_backend.features.patient.service;

import com.omnilinks.omnicare_backend.features.patient.dto.FamilyMemberRequest;
import com.omnilinks.omnicare_backend.features.patient.dto.FamilyMemberResponse;
import com.omnilinks.omnicare_backend.features.patient.entity.FamilyMember;
import com.omnilinks.omnicare_backend.features.patient.repository.FamilyMemberRepository;
import com.omnilinks.omnicare_backend.features.passport.dto.MedicalPassportRequest;
import com.omnilinks.omnicare_backend.features.passport.dto.MedicalPassportResponse;
import com.omnilinks.omnicare_backend.features.passport.entity.MedicalPassport;
import com.omnilinks.omnicare_backend.features.passport.repository.MedicalPassportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final FamilyMemberRepository familyMemberRepository;
    private final MedicalPassportRepository medicalPassportRepository;

    public List<FamilyMemberResponse> getAllFamilyMembers(UUID ownerId) {
        return familyMemberRepository.findByOwnerId(ownerId).stream()
                .map(FamilyMemberResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public FamilyMemberResponse addFamilyMember(UUID ownerId, FamilyMemberRequest request) {
        FamilyMember member = FamilyMember.builder()
                .name(request.getName())
                .age(request.getAge())
                .relationship(request.getRelationship())
                .avatar(request.getAvatar())
                .bloodType(request.getBloodType())
                .chronicConditions(request.getChronicConditions())
                .currentMedications(request.getCurrentMedications() != null
                        ? request.getCurrentMedications().stream()
                                .map(FamilyMemberRequest.MedicationDto::toEntity)
                                .collect(Collectors.toList())
                        : null)
                .criticalAlert(request.getCriticalAlert())
                .phoneNumber(request.getPhoneNumber())
                .qrCode(request.getQrCode())
                .ownerId(ownerId)
                .build();

        FamilyMember savedMember = familyMemberRepository.save(member);

        MedicalPassport passport = MedicalPassport.builder()
                .familyMemberId(savedMember.getId())
                .bloodType(request.getBloodType())
                .chronicConditions(request.getChronicConditions())
                .currentMedications(request.getCurrentMedications() != null
                        ? request.getCurrentMedications().stream()
                                .map(FamilyMemberRequest.MedicationDto::toEntity)
                                .map(med -> MedicalPassport.Medication.builder()
                                        .name(med.getName())
                                        .dosage(med.getDosage())
                                        .frequency(med.getFrequency())
                                        .build())
                                .collect(Collectors.toList())
                        : null)
                .build();

        medicalPassportRepository.save(passport);

        return FamilyMemberResponse.fromEntity(savedMember);
    }

    public MedicalPassportResponse getMedicalPassport(UUID memberId, UUID ownerId) {
        if (!familyMemberRepository.existsByIdAndOwnerId(memberId, ownerId)) {
            throw new IllegalArgumentException("Family member not found or access denied");
        }

        return medicalPassportRepository.findByFamilyMemberId(memberId)
                .map(MedicalPassportResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Medical passport not found"));
    }

    @Transactional
    public MedicalPassportResponse updateMedicalPassport(UUID memberId, UUID ownerId, MedicalPassportRequest request) {
        if (!familyMemberRepository.existsByIdAndOwnerId(memberId, ownerId)) {
            throw new IllegalArgumentException("Family member not found or access denied");
        }

        MedicalPassport passport = medicalPassportRepository.findByFamilyMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Medical passport not found"));

        passport.setBloodType(request.getBloodType());
        passport.setChronicConditions(request.getChronicConditions());
        passport.setCurrentMedications(request.getCurrentMedications() != null
                ? request.getCurrentMedications().stream()
                        .map(MedicalPassportRequest.MedicationDto::toEntity)
                        .collect(Collectors.toList())
                : null);
        passport.setDocuments(request.getDocuments() != null
                ? request.getDocuments().stream()
                        .map(MedicalPassportRequest.MedicalDocumentDto::toEntity)
                        .collect(Collectors.toList())
                : null);

        MedicalPassport updatedPassport = medicalPassportRepository.save(passport);
        return MedicalPassportResponse.fromEntity(updatedPassport);
    }
}
