package com.omnicare.doctor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(0)
public class ConsultationStatusConstraintRepairRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConsultationStatusConstraintRepairRunner.class);

    private final DataSource dataSource;

    public ConsultationStatusConstraintRepairRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE IF EXISTS consultations
                    DROP CONSTRAINT IF EXISTS consultations_status_check
                    """);
            statement.execute("""
                    ALTER TABLE IF EXISTS consultations
                    ADD CONSTRAINT consultations_status_check
                    CHECK (status IN ('PENDING', 'ACCEPTED', 'COMPLETED', 'CANCELLED'))
                    """);
            log.info("[ConsultationStatusConstraintRepairRunner] ensured consultations_status_check allows ACCEPTED");
        }
    }
}
