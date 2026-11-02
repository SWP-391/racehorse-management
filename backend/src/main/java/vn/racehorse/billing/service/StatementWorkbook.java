package vn.racehorse.billing.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.racehorse.billing.dto.Statement;
import vn.racehorse.billing.dto.StatementCategoryGroup;
import vn.racehorse.billing.dto.StatementHorseGroup;
import vn.racehorse.billing.dto.StatementLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Writes statements and invoices as an Excel file from the same {@link Statement} objects the screen is built from, so
 * the file and the screen cannot differ (BR-204, design D4). Amounts are numeric cells. An open month is marked
 * "Tạm tính"; a closed one carries the invoice number and the closing date.
 */
final class StatementWorkbook {

    static final String SUMMARY = "Tổng hợp";
    static final String DETAIL = "Chi tiết";
    static final String PROVISIONAL = "Tạm tính";

    private static final Map<String, String> CATEGORY = Map.of(
            "MEDICINE", "Thuốc và vật tư y tế", "FEED", "Thức ăn", "DAILY_FEE", "Phí chuồng và công chăm sóc",
            "EXAM", "Phí khám và điều trị", "ADJUSTMENT", "Điều chỉnh");

    /** A statement with the closing instant of its invoice (null while the month is open). */
    record Part(Statement statement, Instant closedAt) {
    }

    private StatementWorkbook() {
    }

    static byte[] write(String title, List<Part> parts, ZoneId zone) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper helper = book.getCreationHelper();
            Font bold = book.createFont();
            bold.setBold(true);
            CellStyle head = book.createCellStyle();
            head.setFont(bold);
            CellStyle money = book.createCellStyle();
            money.setDataFormat(helper.createDataFormat().getFormat("#,##0"));
            CellStyle number = book.createCellStyle();
            number.setDataFormat(helper.createDataFormat().getFormat("#,##0.##"));
            CellStyle date = book.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));

            Sheet summary = book.createSheet(SUMMARY);
            text(summary.createRow(0), 0, title, head);
            Row header = summary.createRow(2);
            String[] summaryHeads = {"Chủ ngựa", "Tháng", "Trạng thái", "Số hóa đơn", "Ngày chốt", "Tổng cộng (VND)"};
            for (int i = 0; i < summaryHeads.length; i++) {
                text(header, i, summaryHeads[i], head);
            }
            int r = 3;
            for (Part p : parts) {
                Statement s = p.statement();
                Row row = summary.createRow(r++);
                text(row, 0, s.ownerName(), null);
                text(row, 1, s.month(), null);
                text(row, 2, s.closed() ? "Đã chốt" : PROVISIONAL, null);
                text(row, 3, s.invoiceNumber() == null ? "" : s.invoiceNumber(), null);
                if (p.closedAt() != null) {
                    LocalDate day = p.closedAt().atZone(zone).toLocalDate();
                    Cell cell = row.createCell(4);
                    cell.setCellValue(java.sql.Date.valueOf(day));
                    cell.setCellStyle(date);
                } else {
                    text(row, 4, "", null);
                }
                num(row, 5, s.total().doubleValue(), money);
            }
            if (parts.size() > 1) {
                Row total = summary.createRow(r + 1);
                text(total, 0, "Tổng cộng", head);
                double sum = parts.stream().mapToDouble(p -> p.statement().total().doubleValue()).sum();
                num(total, 5, sum, money);
            }

            Sheet detail = book.createSheet(DETAIL);
            Row dh = detail.createRow(0);
            String[] heads = {"Chủ ngựa", "Ngựa", "Nhóm", "Ngày", "Mô tả", "Số lượng", "Đơn giá (VND)",
                    "Thành tiền (VND)", "Lý do"};
            for (int i = 0; i < heads.length; i++) {
                text(dh, i, heads[i], head);
            }
            int d = 1;
            for (Part p : parts) {
                for (StatementHorseGroup horse : p.statement().horses()) {
                    for (StatementCategoryGroup category : horse.categories()) {
                        for (StatementLine line : category.lines()) {
                            Row row = detail.createRow(d++);
                            text(row, 0, p.statement().ownerName(), null);
                            text(row, 1, horse.horseName(), null);
                            text(row, 2, CATEGORY.getOrDefault(category.category(), category.category()), null);
                            Cell day = row.createCell(3);
                            day.setCellValue(java.sql.Date.valueOf(line.date()));
                            day.setCellStyle(date);
                            text(row, 4, line.description(), null);
                            num(row, 5, line.quantity().doubleValue(), number);
                            num(row, 6, line.unitPrice().doubleValue(), money);
                            num(row, 7, line.amount().doubleValue(), money);
                            text(row, 8, line.reason() == null ? "" : line.reason(), null);
                        }
                    }
                }
            }
            for (Sheet sheet : List.of(summary, detail)) {
                for (int c = 0; c < 9; c++) {
                    sheet.autoSizeColumn(c);
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void num(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
