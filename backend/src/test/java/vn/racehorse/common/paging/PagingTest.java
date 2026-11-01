package vn.racehorse.common.paging;

import org.junit.jupiter.api.Test;
import vn.racehorse.common.exception.ApiException;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The page contract and the search that ignores tone marks (TECH-173, TECH-174; BR-185). */
class PagingTest {

    @Test
    void defaultsAreTwentyRowsFromThePageZero() {
        PageParams p = PageParams.of(null, null, "  ");
        assertThat(p.page()).isZero();
        assertThat(p.size()).isEqualTo(20);
        assertThat(p.q()).isNull();
    }

    @Test
    void aSizeOutsideOneToAHundredIsRefusedNamingSize() {
        // Integration tests raise the largest page for the whole JVM (app.paging.max-size); this test needs the real
        // limit and puts the raised one back for the tests that run after it.
        int before = PageParams.maxSize();
        PageParams.setMaxSize(PageParams.MAX_SIZE);
        try {
            for (int bad : new int[] {0, -1, 101}) {
                assertThatThrownBy(() -> PageParams.of(0, bad, null))
                        .isInstanceOfSatisfying(ApiException.class, e -> {
                            assertThat(e.code()).isEqualTo("VALIDATION_FAILED");
                            assertThat(e.fields()).containsKey("size");
                        });
            }
            assertThat(PageParams.of(0, 100, null).size()).isEqualTo(100);
        } finally {
            PageParams.setMaxSize(before);
        }
    }

    @Test
    void aNegativePageIsRefused() {
        assertThatThrownBy(() -> PageParams.of(-1, 20, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void twentyFiveRowsMakeATwentyAndAFivePage() {
        List<Integer> rows = IntStream.rangeClosed(1, 25).boxed().toList();
        PageResponse<Integer> first = Paging.page(rows, PageParams.of(0, 20, null));
        PageResponse<Integer> second = Paging.page(rows, PageParams.of(1, 20, null));
        assertThat(first.items()).hasSize(20);
        assertThat(second.items()).containsExactly(21, 22, 23, 24, 25);
        assertThat(first.total()).isEqualTo(25);
        assertThat(Paging.page(rows, PageParams.of(9, 20, null)).items()).isEmpty();
    }

    @Test
    void searchIgnoresCaseAndVietnameseToneMarks() {
        assertThat(Search.matches("ngua", "Ngựa")).isTrue();
        assertThat(Search.matches("NGỰA", "ngua")).isTrue();
        assertThat(Search.matches("dang", "Đặng Mạnh Cường")).isTrue();
        assertThat(Search.matches("manh cuong", "Đặng Mạnh Cường")).isTrue();
        assertThat(Search.matches("xyz", "Ngựa", "VN-001")).isFalse();
        assertThat(Search.matches(null, "bất kỳ")).isTrue();
        assertThat(Search.matches("vn-0", "Sao Mai", "VN-001")).isTrue();
    }
}
