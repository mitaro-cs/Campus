package app.groupbase.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuietUpdaterTest {

  @Test
  void onlyAtNightAfterTenQuietMinutes() {
    long quiet = QuietUpdater.QUIET_MS;
    assertThat(QuietUpdater.ready(quiet, 3)).isTrue();
    assertThat(QuietUpdater.ready(quiet - 1, 3)).isFalse();
    assertThat(QuietUpdater.ready(quiet, 1)).isFalse();
    assertThat(QuietUpdater.ready(quiet, 6)).isFalse();
    assertThat(QuietUpdater.ready(quiet, 14)).isFalse();
    assertThat(QuietUpdater.ready(quiet * 6, 5)).isTrue();
  }
}
