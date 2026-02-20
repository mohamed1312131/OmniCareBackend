package com.omnilinks.omnicare_backend.features.patient.dto;

import com.omnilinks.omnicare_backend.features.patient.entity.FamilyMember;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyMemberResponse {

    private String id;
    private String name;
    private Integer age;
    private String relationship;
    private String avatar;
    private String bloodType;
    private List<String> chronicConditions;
    private List<MedicationDto> currentMedications;
    private String criticalAlert;
    private String phoneNumber;
    private String qrCode;

    public static FamilyMemberResponse fromEntity(FamilyMember member) {
        return FamilyMemberResponse.builder()
                .id(member.getId().toString())
                .name(member.getName())
                .age(member.getAge())
                .relationship(member.getRelationship())
                .avatar(member.getAvatar())
                .bloodType(member.getBloodType())
                .chronicConditions(member.getChronicConditions())
                .currentMedications(member.getCurrentMedications() != null
                        ? member.getCurrentMedications().stream()
                                .map(MedicationDto::fromEntity)
                                .collect(Collectors.toList())
                        : null)
                .criticalAlert(member.getCriticalAlert())
                .phoneNumber(member.getPhoneNumber())
                .qrCode(member.getQrCode())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicationDto {
        private String name;
        private String dosage;
        private String frequency;

        public static MedicationDto fromEntity(FamilyMember.Medication medication) {
            return MedicationDto.builder()
                    .name(medication.getName())
                    .dosage(medication.getDosage())
                    .frequency(medication.getFrequency())
                    .build();
        }
    }
}
