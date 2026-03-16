package com.omnicare.auth.seed;

import com.omnicare.family.FamilyMember;
import com.omnicare.family.FamilyMemberRepository;
import com.omnicare.passport.BloodGroup;
import com.omnicare.patient.PatientAllergySeverity;
import com.omnicare.patient.model.Patient;
import com.omnicare.patient.model.PatientAllergy;
import com.omnicare.patient.repository.PatientAllergyRepository;
import com.omnicare.patient.service.PatientService;
import com.omnicare.profile.model.RegistrationStatus;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Profile("seed-realistic")
public class RealisticPatientsSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PatientService patientService;
    private final FamilyMemberRepository familyMemberRepository;
    private final PatientAllergyRepository patientAllergyRepository;

    @Value("${app.seed.realistic-patients.enabled:false}")
    private boolean enabled;

    @Value("${app.seed.realistic-patients.count:50}")
    private int count;

    @Value("${app.seed.realistic-patients.password:Passw0rd!123}")
    private String defaultPassword;

    @Value("${app.seed.realistic-patients.male-names-file:male_names.txt}")
    private String maleNamesFile;

    @Value("${app.seed.realistic-patients.female-names-file:female_names.txt}")
    private String femaleNamesFile;

    @Value("${app.seed.realistic-patients.last-names-file:last_names.txt}")
    private String lastNamesFile;

    public RealisticPatientsSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PatientService patientService,
            FamilyMemberRepository familyMemberRepository,
            PatientAllergyRepository patientAllergyRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientService = patientService;
        this.familyMemberRepository = familyMemberRepository;
        this.patientAllergyRepository = patientAllergyRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!enabled) {
            return;
        }

        List<String> maleNames = readNamesFile(maleNamesFile);
        List<String> femaleNames = readNamesFile(femaleNamesFile);
        List<String> lastNames = readNamesFile(lastNamesFile);

        if (maleNames.isEmpty() || femaleNames.isEmpty() || lastNames.isEmpty()) {
            throw new IllegalStateException("Name lists are empty. Check *_names.txt files.");
        }

        Random r = new Random();

        for (int i = 0; i < count; i++) {
            boolean female = r.nextBoolean();
            String first = pick(r, female ? femaleNames : maleNames);
            String last = pick(r, lastNames);

            String email = toEmail(first, last);
            if (userRepository.findByEmail(email).isPresent()) {
                email = toEmail(first, last) + "+" + System.currentTimeMillis() + "@example.tn";
            }

            String fullName = (first + " " + last).trim();
            String phone = randomTunPhone(r);

            int age = 18 + r.nextInt(63);
            LocalDate dob = LocalDate.now().minus(Period.ofYears(age)).minusDays(r.nextInt(365));

            User user = new User(email, fullName);
            user.setRole(UserRole.PATIENT);
            user.setRegistrationStatus(RegistrationStatus.ACTIVE);
            user.setEmailVerified(true);
            user.setPasswordHash(passwordEncoder.encode(defaultPassword));
            user.setFirstName(first);
            user.setLastName(last);
            user.setPhoneNumber(phone);
            user.setPhoneVerified(false);
            user.setBloodGroup(randomBloodGroup(r));

            Map<String, Object> medicalInfo = new HashMap<>();
            medicalInfo.put("dateOfBirth", dob.toString());
            medicalInfo.put("gender", female ? "F" : "M");
            user.setMedicalInfo(medicalInfo);

            userRepository.save(user);

            Patient patient = patientService.ensureForUser(user);

            seedAllergies(r, patient, user.getEmail());

            boolean hasFamily = r.nextInt(100) < 60;
            if (hasFamily) {
                int membersCount = 1 + r.nextInt(5);
                for (int mi = 0; mi < membersCount; mi++) {
                    seedFamilyMember(r, user, last, maleNames, femaleNames);
                }
            }
        }
    }

    private void seedFamilyMember(Random r, User owner, String ownerLastName, List<String> maleNames, List<String> femaleNames) {
        String[] relationships = new String[]{"SPOUSE", "CHILD", "PARENT", "SIBLING"};
        String relationship = relationships[r.nextInt(relationships.length)];

        boolean female = r.nextBoolean();
        String first = pick(r, female ? femaleNames : maleNames);

        boolean keepLast = !"SPOUSE".equalsIgnoreCase(relationship) ? (r.nextInt(100) < 85) : (r.nextInt(100) < 40);
        String last = keepLast ? ownerLastName : ownerLastName + "i";
        String fullName = (first + " " + last).trim();

        LocalDate birthDate;
        if ("CHILD".equalsIgnoreCase(relationship)) {
            int age = 1 + r.nextInt(25);
            birthDate = LocalDate.now().minusYears(age).minusDays(r.nextInt(365));
        } else if ("PARENT".equalsIgnoreCase(relationship)) {
            int age = 45 + r.nextInt(40);
            birthDate = LocalDate.now().minusYears(age).minusDays(r.nextInt(365));
        } else {
            int age = 18 + r.nextInt(63);
            birthDate = LocalDate.now().minusYears(age).minusDays(r.nextInt(365));
        }

        Map<String, Object> med = new HashMap<>();
        med.put("dateOfBirth", birthDate.toString());
        med.put("gender", female ? "F" : "M");

        FamilyMember fm = new FamilyMember(owner, fullName, relationship);
        fm.setBirthDate(birthDate);
        fm.setGender(female ? "F" : "M");
        fm.setBloodGroup(randomBloodGroup(new Random()));
        fm.setMedicalInfo(med);

        familyMemberRepository.save(fm);
        patientService.ensureForFamilyMember(owner, fm);
    }

    private void seedAllergies(Random r, Patient patient, String createdByEmail) {
        String[] substances = new String[]{"Penicillin", "Ibuprofen", "Aspirin", "Amoxicillin", "Latex"};
        int count = r.nextInt(4);
        for (int i = 0; i < count; i++) {
            String substance = substances[r.nextInt(substances.length)];
            if (patientAllergyRepository.existsByPatientIdAndSubstanceIgnoreCase(patient.getId(), substance)) {
                continue;
            }

            PatientAllergy allergy = new PatientAllergy(patient, substance);
            allergy.setSeverity(randomSeverity(r));
            patientAllergyRepository.save(allergy);
        }
    }

    private static PatientAllergySeverity randomSeverity(Random r) {
        int v = r.nextInt(100);
        if (v < 15) return PatientAllergySeverity.LOW;
        if (v < 55) return PatientAllergySeverity.MEDIUM;
        if (v < 90) return PatientAllergySeverity.HIGH;
        return PatientAllergySeverity.UNKNOWN;
    }

    private static BloodGroup randomBloodGroup(Random r) {
        BloodGroup[] all = BloodGroup.values();
        return all[r.nextInt(all.length)];
    }

    private static String pick(Random r, List<String> list) {
        return list.get(r.nextInt(list.size()));
    }

    private static String randomTunPhone(Random r) {
        char[] starts = new char[]{'2', '5', '9'};
        char start = starts[r.nextInt(starts.length)];
        int rest = r.nextInt(10_000_000);
        return start + String.format("%07d", rest);
    }

    private static String toEmail(String firstName, String lastName) {
        String first = normalizeEmailToken(firstName);
        String last = normalizeEmailToken(lastName);
        String base = (first + "." + last).replaceAll("\\.+", ".");
        base = base.replaceAll("(^\\.)|(\\.$)", "");
        return base + "@example.tn";
    }

    private static String normalizeEmailToken(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase(Locale.ROOT);
        s = s.replace("'", "");
        s = s.replaceAll("\\s+", ".");
        s = s.replaceAll("[^a-z0-9._-]", "");
        return s;
    }

    private static List<String> readNamesFile(String filePath) {
        try {
            Path p = Path.of(filePath);
            if (!p.isAbsolute()) {
                p = Path.of(System.getProperty("user.dir")).resolve(filePath);
            }
            if (!Files.exists(p)) {
                return List.of();
            }
            return Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}
