package vn.racehorse.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.racehorse.common.TestClock;
import vn.racehorse.stock.dto.StockNoticeResult;
import vn.racehorse.stock.service.StockNoticeService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The daily stock notice: once per club date, medicines at zero also to the Veterinarian (BR-176, AC-335, AC-336). */
class StockNoticeTest extends StockTestBase {

    @Autowired
    StockNoticeService notices;

    @Test
    void AC_335_theClubManagerGetsOneNoticePerDateListingTheFlaggedItems() throws Exception {
        JsonNode item = supply("FEED", 1000, 5);
        long horse = observedHorse();
        ration(horse, Map.of(item.get("id").asLong(), 10));
        shiftToClubDay(20, 8);
        StockNoticeResult first = notices.sendDailyNotice();
        assertThat(first.sent()).isTrue();
        assertThat(hasNotification(manager, item.get("name").asText())).isTrue();
        // The same date sends nothing a second time, even if called again (a restart does the same).
        assertThat(notices.sendDailyNotice().sent()).isFalse();
        assertThat(notificationsContaining(manager, "Kho cần nhập")).isEqualTo(1);
        TestClock.reset();
    }

    @Test
    void AC_336_aMedicineAtZeroIsAlsoToldToTheVeterinarianAndNothingIsSentBeforeSevenOClock() throws Exception {
        JsonNode medicine = supply("MEDICINE", 1000, 0);
        shiftToClubDay(21, 6);
        assertThat(notices.sendDailyNotice().sent()).isFalse();
        shiftToClubDay(21, 8);
        assertThat(notices.sendDailyNotice().sent()).isTrue();
        assertThat(hasNotification(vet, medicine.get("name").asText())).isTrue();
        assertThat(hasNotification(manager, medicine.get("name").asText())).isTrue();
        assertThat(hasNotification(trainer, medicine.get("name").asText())).isFalse();
        TestClock.reset();
    }
}
