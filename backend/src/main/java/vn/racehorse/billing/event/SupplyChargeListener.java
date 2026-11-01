package vn.racehorse.billing.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.service.ChargeService;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.service.SupplyUseReversed;
import vn.racehorse.stock.service.SupplyUsed;

import java.math.BigDecimal;

/**
 * Charges follow the stock ledger: a use creates a charge, a reversal removes it, in the same transaction as the
 * mark or the care record, so a charge exists exactly when its fact does (BR-179, TECH-166, XDEP-023).
 */
@Component
class SupplyChargeListener {

    private final ChargeService charges;
    private final SupplyItemRepository supplies;

    SupplyChargeListener(ChargeService charges, SupplyItemRepository supplies) {
        this.charges = charges;
        this.supplies = supplies;
    }

    @EventListener
    void onUsed(SupplyUsed e) {
        SupplyItem item = supplies.findById(e.supplyItemId()).orElseThrow();
        Charge.Category category = e.source() == SupplyUsed.Source.CARE ? Charge.Category.MEDICINE : Charge.Category.FEED;
        String what = category == Charge.Category.MEDICINE ? "Thuốc và vật tư" : "Thức ăn";
        charges.record(new ChargeSpec(e.horseId(), null, category, e.date(),
                what + ": " + item.getName() + " (" + item.getUnit() + ")", BigDecimal.valueOf(e.quantity()),
                e.unitPrice(), null, "SUPPLY:" + e.refType() + ":" + e.refId() + ":" + e.supplyItemId(),
                e.refType() + ":" + e.refId(), null, null));
    }

    @EventListener
    void onReversed(SupplyUseReversed e) {
        charges.voidBySource(e.refType() + ":" + e.refId(), "Hủy đánh dấu");
    }
}
