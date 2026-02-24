package com.omnicare.patient;

import com.omnicare.family.FamilyMember;
import com.omnicare.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PatientType type;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    private FamilyMember familyMember;

    protected Patient() {
    }

    public static Patient forUser(User ownerUser) {
        Patient p = new Patient();
        p.ownerUser = ownerUser;
        p.type = PatientType.USER;
        p.user = ownerUser;
        return p;
    }

    public static Patient forFamilyMember(User ownerUser, FamilyMember familyMember) {
        Patient p = new Patient();
        p.ownerUser = ownerUser;
        p.type = PatientType.FAMILY_MEMBER;
        p.familyMember = familyMember;
        return p;
    }

    public UUID getId() {
        return id;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public PatientType getType() {
        return type;
    }

    public User getUser() {
        return user;
    }

    public FamilyMember getFamilyMember() {
        return familyMember;
    }
}
