package com.omnicare.reminder.repository;

import com.omnicare.reminder.model.PatientReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientReminderRepository extends JpaRepository<PatientReminder, UUID> {

    List<PatientReminder> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    Optional<PatientReminder> findByIdAndPatientId(UUID id, UUID patientId);
}
