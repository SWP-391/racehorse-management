package vn.racehorse.stable.dto;

/** One stall of an area; {@code horse} is null for an empty placeholder. */
public record StallSlotView(int slot, StallBarnRow horse) {
}
