package app.groupbase.status;

import app.groupbase.config.GroupbaseProperties;
import app.groupbase.desktop.DesktopBridge;
import app.groupbase.store.SettingsStore;
import app.groupbase.sync.LiveUpdates;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Бесшовное обновление (1.0.2): новая версия ставится сама ночью, когда на сайте десять минут
 * никого нет (окно на компьютере хоста не в счёт), – группа перезапуска не замечает. Раз на версию:
 * не вышло – до утра не повторяем, кнопка «Обновить сейчас» остаётся. Выключается в «Управление →
 * Версия и обновления».
 */
@Component
public class QuietUpdater {

  static final String SETTING = "updates.night";
  static final long QUIET_MS = 10 * 60_000;
  static final int FROM_HOUR = 2;
  static final int TO_HOUR = 6;

  private final DesktopBridge bridge;
  private final LiveUpdates live;
  private final SettingsStore settings;
  private final Clock clock;
  private final GroupbaseProperties props;

  private long quietSince = -1;
  private String askedFor;

  public QuietUpdater(
      DesktopBridge bridge,
      LiveUpdates live,
      SettingsStore settings,
      Clock clock,
      GroupbaseProperties props) {
    this.bridge = bridge;
    this.live = live;
    this.settings = settings;
    this.clock = clock;
    this.props = props;
  }

  public boolean enabled() {
    return !"off".equals(settings.get(SETTING).orElse("on"));
  }

  public void setEnabled(boolean on) {
    settings.set(SETTING, on ? "on" : "off");
  }

  @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1M")
  void scheduled() {
    tick();
  }

  /** Тихо достаточно долго и сейчас ночь (с 2 до 6 по часам сайта). */
  static boolean ready(long quietFor, int hour) {
    return quietFor >= QUIET_MS && hour >= FROM_HOUR && hour < TO_HOUR;
  }

  /** Шаг раз в минуту; true – попросили оболочку обновиться. */
  synchronized boolean tick() {
    String version = bridge.availableUpdate();
    if (!bridge.enabled() || bridge.updating() || version == null || !enabled()) {
      quietSince = -1;
      return false;
    }
    long now = clock.millis();
    if (live.anyoneRemote()) {
      quietSince = -1;
      return false;
    }
    if (quietSince < 0) {
      quietSince = now;
    }
    int hour = Instant.ofEpochMilli(now).atZone(props.timezone()).getHour();
    if (version.equals(askedFor) || !ready(now - quietSince, hour)) {
      return false;
    }
    askedFor = version;
    bridge.requestUpdate();
    return true;
  }
}
