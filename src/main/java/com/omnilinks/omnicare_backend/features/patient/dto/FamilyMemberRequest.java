package com.omnilinks.omnicare_backend.features.patient.dto;

import com.omnilinks.omnicare_backend.features.patient.entity.FamilyMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyMemberRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Age is required")
    private Integer age;

    @NotBlank(message = "Relationship is required")
    private String relationship;

    private String avatar;

    @NotBlank(message = "Blood type is required")
    private String bloodType;

    private List<String> chronicConditions;

    private List<MedicationDto> currentMedications;

    private String criticalAlert;

    private String phoneNumber;

    private String qrCode;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicationDto {
        private String name;
        private String dosage;
        private String frequency;

        public FamilyMember.Medication toEntity() {
            return FamilyMember.Medication.builder()
                    .name(name)
                    .dosage(dosage)
                    .frequency(frequency)
                    .build();
        }
    }
}
