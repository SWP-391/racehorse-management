package vn.racehorse.horse.entity;

/** The six ancestor positions of a two-generation pedigree (BR-004), each with the sex it requires (BR-005). */
public enum PedigreePosition {
    SIRE(Sex.MALE),
    DAM(Sex.FEMALE),
    SIRE_SIRE(Sex.MALE),
    SIRE_DAM(Sex.FEMALE),
    DAM_SIRE(Sex.MALE),
    DAM_DAM(Sex.FEMALE);

    private final Sex requiredSex;

    PedigreePosition(Sex requiredSex) {
        this.requiredSex = requiredSex;
    }

    public Sex requiredSex() {
        return requiredSex;
    }
}
