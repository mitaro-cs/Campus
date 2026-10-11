package app.groupbase.sync;

import app.groupbase.desktop.DesktopBridge;
import org.springframework.stereotype.Component;

/**
 * Бесшовное обновление (1.0.2): оболочка останавливает сервер ради новой версии (или перезапуск
 * после восстановления, смены сети) – открытые страницы узнают об этом до остановки.
 */
@Component
class RestartNotice {

  RestartNotice(DesktopBridge bridge, LiveUpdates live) {
    bridge.onGoingDown(live::restarting);
  }
}
