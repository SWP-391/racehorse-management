package vn.racehorse.stock.service;

/** Published when every use line of one care event or chore entry has been reversed. */
public record SupplyUseReversed(String refType, Long refId) {
}
