package com.omnicare.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DoctorDocumentRepository extends JpaRepository<DoctorDocument, UUID> {

    List<DoctorDocument> findAllByDoctorIdOrderByCreatedAtDesc(UUID doctorId);
}
