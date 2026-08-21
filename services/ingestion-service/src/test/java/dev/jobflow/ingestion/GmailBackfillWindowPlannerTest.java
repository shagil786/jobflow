package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GmailBackfillWindowPlannerTest {
    private final GmailBackfillWindowPlanner planner = new GmailBackfillWindowPlanner();

    @Test
    void plansNewestOneDayThenContiguousSevenDayWindowsFromNewestToOldest() {
        Instant from = Instant.parse("2026-06-22T12:00:00Z");
        Instant to = Instant.parse("2026-08-21T12:00:00Z");

        List<BackfillWindow> windows = planner.plan(from, to);

        assertThat(windows).isNotEmpty();
        assertThat(windows.get(0).from()).isEqualTo(Instant.parse("2026-08-20T12:00:00Z"));
        assertThat(windows.get(0).to()).isEqualTo(to);
        assertThat(windows.get(0).priority()).isEqualTo(BackfillPriority.HIGH);
        assertThat(windows).allSatisfy(window -> assertThat(window.to()).isAfter(window.from()));
        assertThat(windows).allSatisfy(window -> assertThat(java.time.Duration.between(window.from(), window.to())).isLessThanOrEqualTo(java.time.Duration.ofDays(7)));
        for (int index = 1; index < windows.size(); index++) {
            assertThat(windows.get(index).to()).isEqualTo(windows.get(index - 1).from());
            assertThat(windows.get(index).priority()).isEqualTo(BackfillPriority.NORMAL);
        }
        assertThat(windows.get(windows.size() - 1).from()).isEqualTo(from);
    }

    @Test
    void rejectsInvalidRanges() {
        Instant point = Instant.parse("2026-08-21T12:00:00Z");
        assertThatThrownBy(() -> planner.plan(point, point)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(point.plusSeconds(1), point)).isInstanceOf(IllegalArgumentException.class);
    }
}
