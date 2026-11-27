package vn.racehorse.horse.entity;

import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The three places of a horse's care team (BR-256). One enum knows the account role, the column of the main person
 * and of the substitute, so move, substitute, limit and visibility never repeat the three-way choice.
 */
public enum CareRole {
    HEAD_TRAINER(Role.HEAD_TRAINER, "Huấn luyện viên", "main_trainer_id", "temp_trainer_id", "temp_until",
            "mainTrainer", "tempTrainer", "tempUntil"),
    VETERINARIAN(Role.VETERINARIAN, "Bác sĩ thú y", "vet_id", "temp_vet_id", "temp_vet_until",
            "vet", "tempVet", "tempVetUntil"),
    GROOM(Role.GROOM, "Nhân viên chăm sóc", "groom_id", "temp_groom_id", "temp_groom_until",
            "groom", "tempGroom", "tempGroomUntil");

    private final Role role;
    private final String label;
    private final String mainColumn;
    private final String tempColumn;
    private final String untilColumn;
    private final String mainProperty;
    private final String tempProperty;
    private final String untilProperty;

    CareRole(Role role, String label, String mainColumn, String tempColumn, String untilColumn,
             String mainProperty, String tempProperty, String untilProperty) {
        this.mainProperty = mainProperty;
        this.tempProperty = tempProperty;
        this.untilProperty = untilProperty;
        this.role = role;
        this.label = label;
        this.mainColumn = mainColumn;
        this.tempColumn = tempColumn;
        this.untilColumn = untilColumn;
    }

    public Role role() {
        return role;
    }

    /** The Vietnamese name of the role, used in refusals ("Bác sĩ thú y", ...). */
    public String label() {
        return label;
    }

    public String mainColumn() {
        return mainColumn;
    }

    public String tempColumn() {
        return tempColumn;
    }

    public String untilColumn() {
        return untilColumn;
    }

    /** JPA attribute names of the same three places, for specifications. */
    public String mainProperty() {
        return mainProperty;
    }

    public String tempProperty() {
        return tempProperty;
    }

    public String untilProperty() {
        return untilProperty;
    }

    public static CareRole of(Role role) {
        for (CareRole c : values()) {
            if (c.role == role) {
                return c;
            }
        }
        return null;
    }

    public UserAccount main(Horse h) {
        return switch (this) {
            case HEAD_TRAINER -> h.getMainTrainer();
            case VETERINARIAN -> h.getVet();
            case GROOM -> h.getGroom();
        };
    }

    public UserAccount temp(Horse h) {
        return switch (this) {
            case HEAD_TRAINER -> h.getTempTrainer();
            case VETERINARIAN -> h.getTempVet();
            case GROOM -> h.getTempGroom();
        };
    }

    public LocalDate until(Horse h) {
        return switch (this) {
            case HEAD_TRAINER -> h.getTempUntil();
            case VETERINARIAN -> h.getTempVetUntil();
            case GROOM -> h.getTempGroomUntil();
        };
    }

    public void setMain(Horse h, UserAccount person, Instant now) {
        switch (this) {
            case HEAD_TRAINER -> h.setMainTrainer(person, now);
            case VETERINARIAN -> h.setVet(person, now);
            case GROOM -> h.setGroom(person, now);
        }
    }

    public void setTemp(Horse h, UserAccount person, LocalDate until, Instant now) {
        switch (this) {
            case HEAD_TRAINER -> h.lendTo(person, until, now);
            case VETERINARIAN -> h.substituteVet(person, until, now);
            case GROOM -> h.substituteGroom(person, until, now);
        }
    }

    /** The substitute while today is before the return date, else the main person (BR-156, BR-258). */
    public UserAccount effective(Horse h, LocalDate today) {
        UserAccount t = temp(h);
        LocalDate until = until(h);
        return t != null && until != null && today.isBefore(until) ? t : main(h);
    }
}
