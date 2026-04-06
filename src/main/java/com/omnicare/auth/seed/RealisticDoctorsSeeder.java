package com.omnicare.auth.seed;

import com.omnicare.doctor.model.Doctor;
import com.omnicare.doctor.repository.DoctorRepository;
import com.omnicare.profile.model.RegistrationStatus;
import com.omnicare.profile.model.User;
import com.omnicare.profile.model.UserRole;
import com.omnicare.profile.repository.UserRepository;
import com.omnicare.provider.model.Provider;
import com.omnicare.provider.model.ProviderType;
import com.omnicare.provider.repository.ProviderRepository;
import com.omnicare.provider.service.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class RealisticDoctorsSeeder implements CommandLineRunner {

        private static final Logger log = LoggerFactory.getLogger(RealisticDoctorsSeeder.class);
        private static final double TUNIS_CENTER_LAT = 36.8065d;
        private static final double TUNIS_CENTER_LNG = 10.1815d;

        private final UserRepository userRepository;
        private final ProviderRepository providerRepository;
        private final DoctorRepository doctorRepository;
        private final ProviderService providerService;
        private final PasswordEncoder passwordEncoder;

        @Value("${app.seed.realistic-doctors.enabled:false}")
        private boolean enabled;

        @Value("${app.seed.realistic-doctors.password:Passw0rd!123}")
        private String defaultPassword;

        public RealisticDoctorsSeeder(
                        UserRepository userRepository,
                        ProviderRepository providerRepository,
                        DoctorRepository doctorRepository,
                        ProviderService providerService,
                        PasswordEncoder passwordEncoder) {
                this.userRepository = userRepository;
                this.providerRepository = providerRepository;
                this.doctorRepository = doctorRepository;
                this.providerService = providerService;
                this.passwordEncoder = passwordEncoder;
        }

        public record SeededDoctor(
                        String email,
                        String fullName,
                        String specialty,
                        boolean online,
                        Integer serviceRadiusKm,
                        BigDecimal rating,
                        Integer totalReviews,
                        Integer indicativePriceTnd,
                        Double latitude,
                        Double longitude,
                        Double distanceKm,
                        String bucket,
                        boolean created) {
        }

        public record SeedSummary(
                        int createdCount,
                        int updatedCount,
                        String defaultPassword,
                        List<SeededDoctor> doctors) {
        }

        private record DoctorSeedSpec(
                        String email,
                        String firstName,
                        String lastName,
                        String specialty,
                        int experienceYears,
                        String medicalLicenseNumber,
                        String phoneNumber,
                        LocalDate dateOfBirth,
                        BigDecimal rating,
                        int totalReviews,
                        int indicativePriceTnd,
                        boolean online,
                        int serviceRadiusKm,
                        double latitude,
                        double longitude,
                        String bucket) {
        }

        @Override
        @Transactional
        public void run(String... args) {
                if (!enabled) {
                        return;
                }
                seedDoctors();
        }

        @Transactional
        public SeedSummary seedDoctors() {
                List<DoctorSeedSpec> specs = doctorSpecs();
                int createdCount = 0;
                int updatedCount = 0;
                java.util.ArrayList<SeededDoctor> seeded = new java.util.ArrayList<>();

                for (DoctorSeedSpec spec : specs) {
                        User existing = userRepository.findByEmail(spec.email()).orElse(null);
                        boolean created = existing == null;
                        User user = existing == null
                                        ? new User(spec.email(), (spec.firstName() + " " + spec.lastName()).trim())
                                        : existing;

                        user.setName((spec.firstName() + " " + spec.lastName()).trim());
                        user.setRole(UserRole.DOCTOR);
                        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
                        user.setEmailVerified(true);
                        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
                        user.setFirstName(spec.firstName());
                        user.setLastName(spec.lastName());
                        user.setPhoneNumber(spec.phoneNumber());
                        user.setPhoneVerified(false);
                        user.setDateOfBirth(spec.dateOfBirth());
                        user.setGender(inferGender(spec.firstName()));
                        user = userRepository.save(user);

                        Provider provider = providerService.ensureForProfessionalUser(user);
                        provider.setType(ProviderType.DOCTOR);
                        provider.setOnline(spec.online());
                        provider.setServiceRadiusKm(spec.serviceRadiusKm());
                        provider.setRating(spec.rating());
                        provider.setTotalReviews(spec.totalReviews());
                        provider.setLatitude(spec.latitude());
                        provider.setLongitude(spec.longitude());
                        provider = providerRepository.save(provider);

                        final Provider seededProvider = provider;
                        Doctor doctor = doctorRepository.findByProviderId(seededProvider.getId())
                                        .orElseGet(() -> new Doctor(seededProvider));
                        doctor.setProvider(provider);
                        doctor.setSpecialty(spec.specialty());
                        doctor.setExperienceYears(spec.experienceYears());
                        doctor.setMedicalLicenseNumber(spec.medicalLicenseNumber());
                        doctor = doctorRepository.save(doctor);

                        double distanceKm = haversineKm(TUNIS_CENTER_LAT, TUNIS_CENTER_LNG, spec.latitude(),
                                        spec.longitude());
                        BigDecimal roundedDistance = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
                        log.info(
                                        "[RealisticDoctorsSeeder] doctor={} specialty={} online={} rating={} reviews={} priceTnd={} lat={} lng={} distanceFromTunisCenterKm={} bucket={}",
                                        user.getEmail(),
                                        doctor.getSpecialty(),
                                        provider.isOnline(),
                                        provider.getRating(),
                                        provider.getTotalReviews(),
                                        spec.indicativePriceTnd(),
                                        provider.getLatitude(),
                                        provider.getLongitude(),
                                        roundedDistance,
                                        spec.bucket());

                        seeded.add(new SeededDoctor(
                                        user.getEmail(),
                                        user.getName(),
                                        doctor.getSpecialty(),
                                        provider.isOnline(),
                                        provider.getServiceRadiusKm(),
                                        provider.getRating(),
                                        provider.getTotalReviews(),
                                        spec.indicativePriceTnd(),
                                        provider.getLatitude(),
                                        provider.getLongitude(),
                                        roundedDistance.doubleValue(),
                                        spec.bucket(),
                                        created));

                        if (created) {
                                createdCount++;
                        } else {
                                updatedCount++;
                        }
                }

                return new SeedSummary(createdCount, updatedCount, defaultPassword, seeded);
        }

        private static String inferGender(String firstName) {
                if (firstName == null || firstName.isBlank()) {
                        return "N/A";
                }
                return switch (firstName.trim().toLowerCase()) {
                        case "amal", "sarra", "ines", "mariem", "yasmine", "aya" -> "F";
                        default -> "M";
                };
        }

        private static List<DoctorSeedSpec> doctorSpecs() {
                return List.of(
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor01@dev.local",
                                                "Ahmed",
                                                "Ben Salah",
                                                "Generaliste",
                                                14,
                                                "DISC-DR-0001",
                                                "20000001",
                                                LocalDate.of(1984, 3, 12),
                                                new BigDecimal("4.9"),
                                                152,
                                                90,
                                                true,
                                                5,
                                                36.8065d,
                                                10.1815d,
                                                "UNDER_2_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor02@dev.local",
                                                "Ines",
                                                "Trabelsi",
                                                "Pediatre",
                                                11,
                                                "DISC-DR-0002",
                                                "20000002",
                                                LocalDate.of(1987, 7, 5),
                                                new BigDecimal("4.7"),
                                                98,
                                                110,
                                                true,
                                                6,
                                                36.8109d,
                                                10.1778d,
                                                "UNDER_2_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor03@dev.local",
                                                "Karim",
                                                "Mansouri",
                                                "Kine",
                                                9,
                                                "DISC-DR-0003",
                                                "20000003",
                                                LocalDate.of(1989, 11, 23),
                                                new BigDecimal("4.3"),
                                                64,
                                                80,
                                                false,
                                                8,
                                                36.8018d,
                                                10.1884d,
                                                "UNDER_2_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor04@dev.local",
                                                "Mariem",
                                                "Gharbi",
                                                "Generaliste",
                                                16,
                                                "DISC-DR-0004",
                                                "20000004",
                                                LocalDate.of(1982, 1, 19),
                                                new BigDecimal("4.8"),
                                                145,
                                                100,
                                                true,
                                                12,
                                                36.8625d,
                                                10.1956d,
                                                "UNDER_10_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor05@dev.local",
                                                "Yasmine",
                                                "Ayari",
                                                "Pediatre",
                                                8,
                                                "DISC-DR-0005",
                                                "20000005",
                                                LocalDate.of(1991, 5, 14),
                                                new BigDecimal("4.1"),
                                                51,
                                                95,
                                                true,
                                                10,
                                                36.8408d,
                                                10.1547d,
                                                "UNDER_10_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor06@dev.local",
                                                "Amal",
                                                "Hammami",
                                                "Kine",
                                                13,
                                                "DISC-DR-0006",
                                                "20000006",
                                                LocalDate.of(1986, 9, 2),
                                                new BigDecimal("3.8"),
                                                37,
                                                75,
                                                false,
                                                15,
                                                36.8471d,
                                                10.2542d,
                                                "UNDER_10_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor07@dev.local",
                                                "Mehdi",
                                                "Bouzid",
                                                "Generaliste",
                                                18,
                                                "DISC-DR-0007",
                                                "20000007",
                                                LocalDate.of(1981, 6, 9),
                                                new BigDecimal("5.0"),
                                                204,
                                                120,
                                                true,
                                                25,
                                                37.2746d,
                                                9.8739d,
                                                "OVER_50_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor08@dev.local",
                                                "Sarra",
                                                "Jlassi",
                                                "Pediatre",
                                                10,
                                                "DISC-DR-0008",
                                                "20000008",
                                                LocalDate.of(1990, 4, 21),
                                                new BigDecimal("4.5"),
                                                88,
                                                105,
                                                true,
                                                20,
                                                36.4000d,
                                                10.6167d,
                                                "OVER_50_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor09@dev.local",
                                                "Firas",
                                                "Khelifi",
                                                "Kine",
                                                7,
                                                "DISC-DR-0009",
                                                "20000009",
                                                LocalDate.of(1993, 12, 1),
                                                new BigDecimal("3.5"),
                                                29,
                                                70,
                                                false,
                                                18,
                                                35.8256d,
                                                10.6369d,
                                                "OVER_50_KM"),
                                new DoctorSeedSpec(
                                                "seed.discovery.doctor10@dev.local",
                                                "Yassine",
                                                "Chaabane",
                                                "Generaliste",
                                                12,
                                                "DISC-DR-0010",
                                                "20000010",
                                                LocalDate.of(1988, 8, 30),
                                                new BigDecimal("4.0"),
                                                58,
                                                85,
                                                true,
                                                30,
                                                34.7406d,
                                                10.7603d,
                                                "OVER_50_KM"));
        }

        private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
                final double earthRadiusKm = 6371.0088d;
                double dLat = Math.toRadians(lat2 - lat1);
                double dLng = Math.toRadians(lng2 - lng1);
                double a = Math.pow(Math.sin(dLat / 2), 2)
                                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                                                * Math.pow(Math.sin(dLng / 2), 2);
                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                return earthRadiusKm * c;
        }
}
