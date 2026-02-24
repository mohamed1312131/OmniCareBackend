package com.omnicare.medication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    @Query(value = "select * from medications m " +
            "where m.name ilike concat('%', :q, '%') or m.dci ilike concat('%', :q, '%') " +
            "order by " +
            "case when m.name ilike concat(:q, '%') then 0 when m.dci ilike concat(:q, '%') then 1 else 2 end, " +
            "lower(m.name) asc " +
            "limit 10",
            nativeQuery = true)
    List<Medication> searchTop10(@Param("q") String q);

    @Query(value = "select distinct m.dci from medications m " +
            "where m.dci is not null and btrim(m.dci) <> '' and lower(m.dci) like lower(concat('%', :q, '%')) " +
            "order by m.dci asc " +
            "limit 20",
            nativeQuery = true)
    List<String> searchDistinctDciTop20(@Param("q") String q);
}
