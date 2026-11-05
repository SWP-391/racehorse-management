package vn.racehorse.training;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A horse has at most one plan Đang thực hiện; several drafts are fine (BR-154, AC-292, AC-293). */
class ActivePlanTest extends TrainingTestBase {

    private String planState(long plan) throws Exception {
        return body(as(trainer, get("/api/plans/" + plan)).andExpect(status().isOk())).get("state").asText();
    }

    @Test
    void AC_292_aSecondActivationIsRefusedNamingTheRunningPlan() throws Exception {
        long horse = eligibleHorse();
        long first = activePlan(horse);
        long second = draftPlan(horse);
        draftPlan(horse); // several Nháp plans may coexist

        as(trainer, post("/api/plans/" + second + "/activate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_PLAN_EXISTS"))
                .andExpect(jsonPath("$.message").value(containsString("Giáo án mùa thu")));
        assertThat(planState(second)).isEqualTo("DRAFT");
        assertThat(planState(first)).isEqualTo("ACTIVE");

        // Once the running plan is completed the next one can start.
        as(trainer, post("/api/plans/" + first + "/complete")).andExpect(status().isOk());
        as(trainer, post("/api/plans/" + second + "/activate")).andExpect(status().isOk());
        assertThat(planState(second)).isEqualTo("ACTIVE");
    }

    @Test
    void AC_293_twoSimultaneousActivationsLeaveExactlyOne() throws Exception {
        long horse = eligibleHorse();
        long a = draftPlan(horse);
        long b = draftPlan(horse);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (long id : new long[] {a, b}) {
                results.add(pool.submit(() -> {
                    go.await();
                    return as(trainer, post("/api/plans/" + id + "/activate")).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        long active = List.of(a, b).stream().filter(id -> {
            try {
                return planState(id).equals("ACTIVE");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();
        assertThat(active).isEqualTo(1);
    }
}
