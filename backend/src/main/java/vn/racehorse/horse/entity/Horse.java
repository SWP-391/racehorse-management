package vn.racehorse.horse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import vn.racehorse.account.entity.UserAccount;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "horse")
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sex sex;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "coat_colour")
    private String coatColour;

    private String breed;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_reason")
    private ArchiveReason archiveReason;

    @Column(name = "archive_note")
    private String archiveNote;

    /** Stall: an area of the catalog and a slot within its capacity, both set or both null (BR-129, BR-164). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(name = "stall_slot")
    private Integer stallSlot;

    /** The old free-text stall that did not fit the catalog, kept until the Club Manager places the horse again. */
    @Column(name = "legacy_stall")
    private String legacyStall;

    /** Photo renditions in photo storage (BR-184); all three are null while the horse has no photo. */
    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "photo_thumb_key")
    private String photoThumbKey;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    /** The one Head Trainer who writes training data of this horse (BR-152); null only for horses from before it. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "main_trainer_id")
    private UserAccount mainTrainer;

    /** A temporary trainer until the date, when the horse returns to the main trainer (BR-156). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "temp_trainer_id")
    private UserAccount tempTrainer;

    @Column(name = "temp_until")
    private LocalDate tempUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Horse() {
    }

    public Horse(Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Set once at registration; the number is generated and never edited afterwards (BR-192). */
    public void assignRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public void setProfile(String name, Sex sex, LocalDate birthDate, String coatColour,
                    String breed, UserAccount owner, Instant now) {
        this.name = name;
        this.sex = sex;
        this.birthDate = birthDate;
        this.coatColour = coatColour;
        this.breed = breed;
        this.owner = owner;
        this.updatedAt = now;
    }

    public void setMainTrainer(UserAccount trainer, Instant now) {
        this.mainTrainer = trainer;
        this.updatedAt = now;
    }

    public void lendTo(UserAccount trainer, LocalDate until, Instant now) {
        this.tempTrainer = trainer;
        this.tempUntil = until;
        this.updatedAt = now;
    }

    public void endLoan(Instant now) {
        lendTo(null, null, now);
    }

    public void archive(ArchiveReason reason, String note, Instant now) {
        this.archivedAt = now;
        this.archiveReason = reason;
        this.archiveNote = note;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        this.archivedAt = null;
        this.archiveReason = null;
        this.archiveNote = null;
        this.updatedAt = now;
    }

    public void placeInStall(Area area, int slot, Instant now) {
        this.area = area;
        this.stallSlot = slot;
        this.legacyStall = null;
        this.updatedAt = now;
    }

    public void clearStall(Instant now) {
        this.area = null;
        this.stallSlot = null;
        this.legacyStall = null;
        this.updatedAt = now;
    }

    public void setPhoto(String key, String thumbKey, Instant now) {
        this.photoKey = key;
        this.photoThumbKey = thumbKey;
        this.photoUpdatedAt = now;
        this.updatedAt = now;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public String getPhotoThumbKey() {
        return photoThumbKey;
    }

    public Instant getPhotoUpdatedAt() {
        return photoUpdatedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Area getArea() {
        return area;
    }

    public Integer getStallSlot() {
        return stallSlot;
    }

    public String getLegacyStall() {
        return legacyStall;
    }

    public UserAccount getMainTrainer() {
        return mainTrainer;
    }

    public UserAccount getTempTrainer() {
        return tempTrainer;
    }

    public LocalDate getTempUntil() {
        return tempUntil;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public Sex getSex() {
        return sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getCoatColour() {
        return coatColour;
    }

    public String getBreed() {
        return breed;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ArchiveReason getArchiveReason() {
        return archiveReason;
    }

    public String getArchiveNote() {
        return archiveNote;
    }
}
