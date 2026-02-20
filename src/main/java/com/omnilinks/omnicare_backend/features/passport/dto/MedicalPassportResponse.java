package com.omnilinks.omnicare_backend.features.passport.dto;

import com.omnilinks.omnicare_backend.features.passport.entity.MedicalPassport;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalPassportResponse {

    private String bloodType;
    private List<String> chronicConditions;
    private List<MedicationDto> currentMedications;
    private List<MedicalDocumentDto> documents;

    public static MedicalPassportResponse fromEntity(MedicalPassport passport) {
        return MedicalPassportResponse.builder()
                .bloodType(passport.getBloodType())
                .chronicConditions(passport.getChronicConditions())
                .currentMedications(passport.getCurrentMedications() != null
                        ? passport.getCurrentMedications().stream()
                                .map(MedicationDto::fromEntity)
                                .collect(Collectors.toList())
                        : null)
                .documents(passport.getDocuments() != null
                        ? passport.getDocuments().stream()
                                .map(MedicalDocumentDto::fromEntity)
                                .collect(Collectors.toList())
                        : null)
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

        public static MedicationDto fromEntity(MedicalPassport.Medication medication) {
            return MedicationDto.builder()
                    .name(medication.getName())
                    .dosage(medication.getDosage())
                    .frequency(medication.getFrequency())
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

        public static MedicalDocumentDto fromEntity(MedicalPassport.MedicalDocument document) {
            return MedicalDocumentDto.builder()
                    .id(document.getId())
                    .name(document.getName())
                    .type(document.getType())
                    .date(document.getDate())
                    .fileUrl(document.getFileUrl())
                    .fileType(document.getFileType())
                    .uploadedAt(document.getUploadedAt())
                    .build();
        }
    }
}
