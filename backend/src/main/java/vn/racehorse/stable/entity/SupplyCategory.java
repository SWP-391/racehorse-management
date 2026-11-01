package vn.racehorse.stable.entity;

/** Supply categories Thức ăn, Thuốc, Dụng cụ (BR-130). */
public enum SupplyCategory {
    FEED,
    MEDICINE,
    TOOLS;

    public static SupplyCategory parse(String code) {
        if (code == null) {
            return null;
        }
        for (SupplyCategory c : values()) {
            if (c.name().equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        return null;
    }
}
