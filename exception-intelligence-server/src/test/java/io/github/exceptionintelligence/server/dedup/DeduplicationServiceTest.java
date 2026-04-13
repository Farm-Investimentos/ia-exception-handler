package io.github.exceptionintelligence.server.dedup;

import io.github.exceptionintelligence.server.config.ServerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationServiceTest {

    private DeduplicationService service() {
        var config = new ServerProperties.DeduplicationProperties();
        config.setEnabled(true);
        config.setTtlMinutes(60);
        return new DeduplicationService(config);
    }

    @Test
    void firstOccurrenceIsNotDuplicate() {
        assertThat(service().checkAndMark("fp-001")).isFalse();
    }

    @Test
    void secondOccurrenceIsDuplicate() {
        var svc = service();
        svc.checkAndMark("fp-001");
        assertThat(svc.checkAndMark("fp-001")).isTrue();
    }

    @Test
    void differentFingerprintsAreIndependent() {
        var svc = service();
        assertThat(svc.checkAndMark("fp-A")).isFalse();
        assertThat(svc.checkAndMark("fp-B")).isFalse();
    }

    @Test
    void disabledServiceNeverDuplicates() {
        var config = new ServerProperties.DeduplicationProperties();
        config.setEnabled(false);
        var svc = new DeduplicationService(config);
        svc.checkAndMark("fp-X");
        assertThat(svc.checkAndMark("fp-X")).isFalse();
    }
}
