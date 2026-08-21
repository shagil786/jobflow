package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GmailSyncScopeTest {
    @Test
    void buildsOnlyTheDedicatedTrackingLabelQuery() {
        assertThat(GmailSyncScope.queryForLabel("JobFlow/Track"))
                .isEqualTo("label:JobFlow/Track");
    }

    @Test
    void rejectsBroadOrEmptyScopes() {
        assertThatThrownBy(() -> GmailSyncScope.queryForLabel("INBOX"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GmailSyncScope.queryForLabel(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesIncrementalCursorAndFlagsStaleRecovery() {
        SyncCursor cursor = new SyncCursor("history-42", "page-7");

        assertThat(cursor.historyId()).isEqualTo("history-42");
        assertThat(cursor.pageToken()).isEqualTo("page-7");
        assertThat(cursor.requiresFullResync("history-42")).isFalse();
        assertThat(cursor.requiresFullResync("history-41")).isTrue();
    }
}
