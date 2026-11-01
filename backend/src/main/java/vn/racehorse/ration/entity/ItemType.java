package vn.racehorse.ration.entity;

/** Ration item types Ngũ cốc, Cỏ, Vitamin (BR-139). */
public enum ItemType {
    GRAIN,
    HAY,
    VITAMIN;

    public static ItemType parse(String code) {
        if (code == null) {
            return null;
        }
        for (ItemType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) {
                return t;
            }
        }
        return null;
    }
}
