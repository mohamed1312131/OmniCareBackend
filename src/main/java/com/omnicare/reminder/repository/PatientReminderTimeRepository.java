package com.omnicare.reminder.repository;

import com.omnicare.reminder.model.PatientReminderTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientReminderTimeRepository extends JpaRepository<PatientReminderTime, UUID> {

    List<PatientReminderTime> findAllByReminderIdOrderByTimeAsc(UUID reminderId);

    void deleteAllByReminderId(UUID reminderId);
}
