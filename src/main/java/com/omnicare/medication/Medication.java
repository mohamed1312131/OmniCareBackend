package com.omnicare.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(
        name = "medications",
        indexes = {
                @Index(name = "idx_medications_name", columnList = "name")
        }
)
public class Medication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "dosage")
    private String dosage;

    @Column(name = "form")
    private String form;

    @Column(name = "dci")
    private String dci;

    @Column(name = "classe")
    private String classe;

    @Column(name = "sous_classe")
    private String sousClasse;

    @Column(name = "indications", columnDefinition = "TEXT")
    private String indications;

    @Column(name = "type")
    private String type;

    protected Medication() {
    }

    public Medication(
            String name,
            String dosage,
            String form,
            String dci,
            String classe,
            String sousClasse,
            String indications,
            String type
    ) {
        this.name = name;
        this.dosage = dosage;
        this.form = form;
        this.dci = dci;
        this.classe = classe;
        this.sousClasse = sousClasse;
        this.indications = indications;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDosage() {
        return dosage;
    }

    public String getForm() {
        return form;
    }

    public String getDci() {
        return dci;
    }

    public String getClasse() {
        return classe;
    }

    public String getSousClasse() {
        return sousClasse;
    }

    public String getIndications() {
        return indications;
    }

    public String getType() {
        return type;
    }
}
