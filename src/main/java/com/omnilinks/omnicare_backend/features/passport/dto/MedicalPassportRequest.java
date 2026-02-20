package com.omnilinks.omnicare_backend.features.passport.dto;

import com.omnilinks.omnicare_backend.features.passport.entity.MedicalPassport;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalPassportRequest {

    @NotBlank(message = "Blood type is required")
    private String bloodType;

    private List<String> chronicConditions;

    private List<MedicationDto> currentMedications;

    private List<MedicalDocumentDto> documents;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicationDto {
        private String name;
        private String dosage;
        private String frequency;

        public MedicalPassport.Medication toEntity() {
            return MedicalPassport.Medication.builder()
                    .name(name)
                    .dosage(dosage)
                    .frequency(frequency)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicalDocumentDto {
        private String id;
        private String name;
        private String type;
        private String date;
        private String fileUrl;
        private String fileType;
        private String uploadedAt;

        public MedicalPassport.MedicalDocument toEntity() {
            return MedicalPassport.MedicalDocument.builder()
                    .id(id)
                    .name(name)
                    .type(type)
                    .date(date)
                    .fileUrl(fileUrl)
                    .fileType(fileType)
                    .uploadedAt(uploadedAt)
                    .build();
        }
    }
}
