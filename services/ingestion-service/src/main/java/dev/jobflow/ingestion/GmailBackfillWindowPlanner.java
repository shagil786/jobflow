package dev.jobflow.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class GmailBackfillWindowPlanner {
    static final int DEFAULT_BATCH_SIZE_DAYS = 7;

    List<BackfillWindow> plan(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Backfill range must have to after from");
        }
        List<BackfillWindow> reverse = new ArrayList<>();
        Instant cursor = to;
        boolean newest = true;
        while (cursor.isAfter(from)) {
            Instant windowFrom = cursor.minus(Duration.ofDays(newest ? 1 : DEFAULT_BATCH_SIZE_DAYS));
            if (windowFrom.isBefore(from)) windowFrom = from;
            reverse.add(new BackfillWindow(windowFrom, cursor, newest ? BackfillPriority.HIGH : BackfillPriority.NORMAL));
            cursor = windowFrom;
            newest = false;
        }
        return Collections.unmodifiableList(reverse);
    }
}
