package vn.racehorse.ration.dto;

import vn.racehorse.horse.entity.Horse;
import vn.racehorse.ration.entity.RationVersion;

import java.util.List;

/**
 * {@code history} is null for a Horse Owner (BR-142); {@code current} is null without a ration (BR-145);
 * {@code needsReview} is derived from the current health status and the current ration tag (TECH-D2).
 */
public record RationView(Horse horse, RationVersion current, List<RationVersion> history, boolean needsReview) {
}
