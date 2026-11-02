package vn.racehorse.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.racehorse.account.entity.Role;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Category totals of a statement and its Excel export (BR-203, BR-204, AC-406 to AC-410). */
class StatementExportTest extends BillingTestBase {

    private static final AtomicInteger YEAR = new AtomicInteger(1990);

    private final java.util.List<YearMonth> used = new java.util.ArrayList<>();

    private YearMonth freshMonth() {
        YearMonth month = YearMonth.of(YEAR.incrementAndGet(), 8);
        used.add(month);
        return month;
    }

    /** The database is shared: a month left open would block the closing of every later month (BR-180). */
    @org.junit.jupiter.api.AfterEach
    void closeUsedMonths() throws Exception {
        for (YearMonth month : used) {
            as(manager, post("/api/billing/months/" + month + "/close"));
        }
        used.clear();
    }

    private void adjustment(long ownerId, Long horse, YearMonth month, int day, Object amount, String reason) throws Exception {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("ownerId", ownerId);
        m.put("horseId", horse);
        m.put("date", month.atDay(day).toString());
        m.put("amount", amount);
        m.put("reason", reason);
        asJson(manager, post("/api/billing/adjustments"), m).andExpect(status().isCreated());
    }

    private byte[] export(vn.racehorse.security.AccountPrincipal who, String url) throws Exception {
        return as(who, get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private XSSFWorkbook book(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private double sumOfColumn(Sheet sheet, int col) {
        double sum = 0;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(col) != null && row.getCell(col).getCellType() == CellType.NUMERIC) {
                sum += row.getCell(col).getNumericCellValue();
            }
        }
        return sum;
    }

    @Test
    void AC_406_categoryTotalsAddUpToTheTotalAndEqualTheGroupedTotals() throws Exception {
        YearMonth month = freshMonth();
        long horse = newHorse();
        adjustment(owner.id(), horse, month, 3, 30000, "a");
        adjustment(owner.id(), null, month, 4, 12000, "b");
        adjustment(owner.id(), horse, month, 5, -2000, "c");
        JsonNode s = statementOf(owner, month);
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode c : s.get("categories")) {
            sum = sum.add(c.get("total").decimalValue());
            BigDecimal grouped = BigDecimal.ZERO;
            for (JsonNode h : s.get("horses")) {
                for (JsonNode cat : h.get("categories")) {
                    if (cat.get("category").asText().equals(c.get("category").asText())) {
                        grouped = grouped.add(cat.get("total").decimalValue());
                    }
                }
            }
            assertThat(c.get("total").decimalValue()).isEqualByComparingTo(grouped);
        }
        assertThat(sum).isEqualByComparingTo(s.get("total").decimalValue());
        assertThat(s.get("total").decimalValue()).isEqualByComparingTo("40000");
    }

    @Test
    void AC_408_theExportHasTheSameLinesAndTotalAsTheScreenWithNumericCells() throws Exception {
        YearMonth month = freshMonth();
        long horse = newHorse();
        adjustment(owner.id(), horse, month, 3, 30000, "một");
        adjustment(owner.id(), null, month, 4, 12000, "hai");
        close(month);
        JsonNode screen = statementOf(owner, month);
        try (XSSFWorkbook wb = book(export(owner, "/api/billing/my-statement/export?month=" + month))) {
            Sheet detail = wb.getSheet("Chi tiết");
            assertThat(detail.getLastRowNum()).isEqualTo(2);
            assertThat(detail.getRow(1).getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sumOfColumn(detail, 7)).isEqualTo(screen.get("total").asDouble());
            Sheet summary = wb.getSheet("Tổng hợp");
            assertThat(summary.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(screen.get("total").asDouble());
            assertThat(summary.getRow(3).getCell(3).getStringCellValue()).isEqualTo(screen.get("invoiceNumber").asText());
        }
    }

    @Test
    void AC_409_anOpenMonthIsMarkedProvisionalAndCarriesNoInvoiceNumber() throws Exception {
        YearMonth month = freshMonth();
        adjustment(owner.id(), null, month, 3, 5000, "mở");
        try (XSSFWorkbook wb = book(export(owner, "/api/billing/my-statement/export?month=" + month))) {
            Sheet summary = wb.getSheet("Tổng hợp");
            assertThat(summary.getRow(3).getCell(2).getStringCellValue()).isEqualTo("Tạm tính");
            assertThat(summary.getRow(3).getCell(3).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void theClubManagerExportsAllOwnersOfAMonthWithTheOwnerOnEveryLine() throws Exception {
        YearMonth month = freshMonth();
        var other = account(Role.HORSE_OWNER);
        adjustment(owner.id(), null, month, 3, 1000, "x");
        adjustment(other.id(), null, month, 4, 2000, "y");
        try (XSSFWorkbook wb = book(export(manager, "/api/billing/statements/export?month=" + month))) {
            Sheet summary = wb.getSheet("Tổng hợp");
            assertThat(summary.getRow(3).getCell(0).getStringCellValue()).isNotBlank();
            assertThat(summary.getRow(4).getCell(0).getStringCellValue()).isNotBlank();
            Sheet detail = wb.getSheet("Chi tiết");
            assertThat(detail.getLastRowNum()).isEqualTo(2);
            assertThat(detail.getRow(1).getCell(0).getStringCellValue()).isNotBlank();
            assertThat(detail.getRow(2).getCell(0).getStringCellValue()).isNotBlank();
            assertThat(sumOfColumn(detail, 7)).isEqualTo(3000.0);
        }
    }

    @Test
    void aClosedInvoiceExportIsStableAfterARename() throws Exception {
        YearMonth month = freshMonth();
        long horse = newHorse();
        adjustment(owner.id(), horse, month, 5, 7000, "gắn ngựa");
        close(month);
        long invoice = body(as(owner, get("/api/billing/invoices").param("month", month.toString()))).get(0).get("id").asLong();
        byte[] first = export(owner, "/api/billing/invoices/" + invoice + "/export");
        asJson(manager, put("/api/horses/" + horse), horseBody("Tên mới " + System.nanoTime() % 1000, "MALE", owner.id()))
                .andExpect(status().isOk());
        byte[] second = export(owner, "/api/billing/invoices/" + invoice + "/export");
        try (XSSFWorkbook a = book(first); XSSFWorkbook b = book(second)) {
            assertThat(b.getSheet("Chi tiết").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo(a.getSheet("Chi tiết").getRow(1).getCell(1).getStringCellValue());
            assertThat(sumOfColumn(b.getSheet("Chi tiết"), 7)).isEqualTo(sumOfColumn(a.getSheet("Chi tiết"), 7));
        }
    }

    private void close(YearMonth month) throws Exception {
        as(manager, post("/api/billing/months/" + month + "/close")).andExpect(status().isOk());
    }

    @Test
    void AC_410_anOwnerExportsOnlyTheirOwnAndOtherRolesAreRefused() throws Exception {
        YearMonth month = freshMonth();
        var other = account(Role.HORSE_OWNER);
        adjustment(other.id(), null, month, 3, 1000, "của người khác");
        as(owner, get("/api/billing/statements/" + other.id() + "/export?month=" + month)).andExpect(status().isForbidden());
        as(owner, get("/api/billing/statements/export?month=" + month)).andExpect(status().isForbidden());
        as(owner, get("/api/billing/statements/" + owner.id() + "/export?month=" + month)).andExpect(status().isOk());
        for (Role role : new Role[] {Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM}) {
            var who = account(role);
            as(who, get("/api/billing/statements/export?month=" + month)).andExpect(status().isForbidden());
            as(who, get("/api/billing/statements/" + other.id() + "/export?month=" + month)).andExpect(status().isForbidden());
            as(who, get("/api/billing/my-statement/export?month=" + month)).andExpect(status().isForbidden());
            as(who, get("/api/billing/invoices/1/export")).andExpect(status().isForbidden());
        }
    }
}
