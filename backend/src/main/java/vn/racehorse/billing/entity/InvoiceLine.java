package vn.racehorse.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A copied line of an invoice. */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "horse_id")
    private Long horseId;

    @Column(name = "horse_name")
    private String horseName;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount;

    private String reason;

    protected InvoiceLine() {
    }

    public InvoiceLine(Long invoiceId, Charge c, String horseName) {
        this.invoiceId = invoiceId;
        this.horseId = c.getHorseId();
        this.horseName = horseName;
        this.category = c.getCategory().name();
        this.description = c.getDescription();
        this.workDate = c.getWorkDate();
        this.chargeDate = c.getChargeDate();
        this.quantity = c.getQuantity();
        this.unitPrice = c.getUnitPrice();
        this.amount = c.getAmount();
        this.reason = c.getReason();
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public Long getHorseId() {
        return horseId;
    }

    public String getHorseName() {
        return horseName;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }
}
