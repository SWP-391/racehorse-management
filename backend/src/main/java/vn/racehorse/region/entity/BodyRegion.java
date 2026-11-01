package vn.racehorse.region.entity;

/**
 * The one catalog of 41 body regions for the whole application (BR-110, BR-096, ba/07, TECH-052): the only place a
 * region name is written. The code is the stable key shared by examinations, incident reports, marks and the 3D model.
 */
public enum BodyRegion {

    HEAD("Đầu", "Head"),
    NECK("Cổ", "Neck"),
    WITHERS("Cao vai", "Withers"),
    L_SHOULDER("Vai trái", "Left shoulder"),
    R_SHOULDER("Vai phải", "Right shoulder"),
    CHEST("Ngực", "Chest"),
    BACK("Lưng", "Back"),
    LOIN("Thắt lưng", "Loin"),
    BELLY("Bụng", "Belly"),
    CROUP("Mông", "Croup"),
    L_HIP("Hông trái", "Left hip"),
    R_HIP("Hông phải", "Right hip"),
    TAIL_BASE("Gốc đuôi", "Tail base"),
    LF_ELBOW("Khuỷu trước trái", "Left fore elbow"),
    LF_FOREARM("Cẳng tay trước trái", "Left fore forearm"),
    LF_CARPUS("Khớp cổ tay (gối trước) trái", "Left fore carpus (knee)"),
    LF_CANNON("Xương ống trước trái", "Left fore cannon bone"),
    LF_FETLOCK("Khớp bàn trước trái", "Left fore fetlock"),
    LF_PASTERN("Cổ chân (pastern) trước trái", "Left fore pastern"),
    LF_HOOF("Móng trước trái", "Left fore hoof"),
    RF_ELBOW("Khuỷu trước phải", "Right fore elbow"),
    RF_FOREARM("Cẳng tay trước phải", "Right fore forearm"),
    RF_CARPUS("Khớp cổ tay (gối trước) phải", "Right fore carpus (knee)"),
    RF_CANNON("Xương ống trước phải", "Right fore cannon bone"),
    RF_FETLOCK("Khớp bàn trước phải", "Right fore fetlock"),
    RF_PASTERN("Cổ chân (pastern) trước phải", "Right fore pastern"),
    RF_HOOF("Móng trước phải", "Right fore hoof"),
    LH_STIFLE("Khớp gối sau trái", "Left hind stifle"),
    LH_GASKIN("Cẳng chân sau trái", "Left hind gaskin"),
    LH_HOCK("Khớp cổ chân sau (hock) trái", "Left hind hock"),
    LH_CANNON("Xương ống sau trái", "Left hind cannon bone"),
    LH_FETLOCK("Khớp bàn sau trái", "Left hind fetlock"),
    LH_PASTERN("Cổ chân (pastern) sau trái", "Left hind pastern"),
    LH_HOOF("Móng sau trái", "Left hind hoof"),
    RH_STIFLE("Khớp gối sau phải", "Right hind stifle"),
    RH_GASKIN("Cẳng chân sau phải", "Right hind gaskin"),
    RH_HOCK("Khớp cổ chân sau (hock) phải", "Right hind hock"),
    RH_CANNON("Xương ống sau phải", "Right hind cannon bone"),
    RH_FETLOCK("Khớp bàn sau phải", "Right hind fetlock"),
    RH_PASTERN("Cổ chân (pastern) sau phải", "Right hind pastern"),
    RH_HOOF("Móng sau phải", "Right hind hoof");

    private final String vietnamese;
    private final String english;

    BodyRegion(String vietnamese, String english) {
        this.vietnamese = vietnamese;
        this.english = english;
    }

    public String vietnamese() {
        return vietnamese;
    }

    public String english() {
        return english;
    }

    /** The region for a code, or null when the code is not catalogued. */
    public static BodyRegion parse(String code) {
        if (code == null) return null;
        try {
            return valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
