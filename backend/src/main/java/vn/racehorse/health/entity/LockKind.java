package vn.racehorse.health.entity;

public enum LockKind {
    /** Placed by the Veterinarian on a Cần theo dõi horse (BR-083). */
    MANUAL,
    /** Placed when status becomes Chấn thương or Cách ly (BR-014). */
    AUTOMATIC,
    /** Placed by the system at registration: "Chờ khám nhập đàn" (BR-008). */
    INTAKE
}
