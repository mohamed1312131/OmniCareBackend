package com.omnicare.medication;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationRepository medicationRepository;

    public MedicationController(MedicationRepository medicationRepository) {
        this.medicationRepository = medicationRepository;
    }

    public record MedicationSearchResult(UUID id, String name, String dosage, String form, String dci, String type) {
        static MedicationSearchResult from(Medication m) {
            return new MedicationSearchResult(m.getId(), m.getName(), m.getDosage(), m.getForm(), m.getDci(), m.getType());
        }
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public MedicationSearchResult getById(Authentication authentication, @PathVariable("id") UUID id) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        Medication m = medicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        return MedicationSearchResult.from(m);
    }

    @GetMapping("/dci")
    public List<String> searchDci(Authentication authentication, @RequestParam("q") String q) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }
        return medicationRepository.searchDistinctDciTop20(q.trim());
    }

    @GetMapping("/search")
    public List<MedicationSearchResult> search(
            @RequestParam("q") String q
    ) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }

        return medicationRepository.searchTop10(q.trim()).stream().map(MedicationSearchResult::from).toList();
    }
}
