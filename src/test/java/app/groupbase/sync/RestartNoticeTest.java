package app.groupbase.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RestartNoticeTest {

  @Test
  void restartIsAnnouncedToPagesAndPolls() {
    LiveUpdates live = new LiveUpdates(null);
    assertThat(live.isRestarting()).isFalse();
    live.restarting();
    assertThat(live.isRestarting()).isTrue();
  }
}
