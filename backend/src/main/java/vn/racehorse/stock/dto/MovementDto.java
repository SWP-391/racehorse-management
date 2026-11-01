package vn.racehorse.stock.dto;

import vn.racehorse.stock.entity.StockMovement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MovementDto(Long id, StockMovement.Kind kind, long delta, long balanceAfter, BigDecimal unitPrice,
                          LocalDate occurredOn, String refType, Long refId, String reason, String actorName,
                          Instant createdAt) {
}
