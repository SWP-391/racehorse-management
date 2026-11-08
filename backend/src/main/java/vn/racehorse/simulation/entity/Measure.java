package vn.racehorse.simulation.entity;

/** What a threshold applies to: heart rate in bpm, speed in km/h (DEC-026). */
public enum Measure {
    HEART_RATE,
    SPEED;

    public String label() {
        return this == HEART_RATE ? "nhịp tim" : "vận tốc";
    }
}
