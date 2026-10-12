package app.groupbase.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopConfigTest {

  @Test
  void keepsPortWhileFreeAndMovesWhenBusy(@TempDir Path data) throws IOException {
    int port;
    try (ServerSocket probe = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      port = probe.getLocalPort();
    }
    java.nio.file.Files.writeString(data.resolve(DesktopConfig.FILE), "port=" + port + "\n");
    assertThat(DesktopConfig.load(data).choosePort()).isEqualTo(port);

    try (ServerSocket busy = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
      assertThat(busy.isBound()).isTrue();
      int moved = DesktopConfig.load(data).choosePort();
      assertThat(moved).isNotEqualTo(port);
      // Новый порт запомнен: при следующем запуске окно хоста откроется по тому же адресу.
      assertThat(DesktopConfig.load(data).port()).isEqualTo(moved);
    }
  }

  @Test
  void windowPortIsKeptBetweenLaunchesAndNeverTheMainOne(@TempDir Path data) throws IOException {
    int main;
    int window;
    try (ServerSocket a = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        ServerSocket b = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      main = a.getLocalPort();
      window = b.getLocalPort();
    }
    java.nio.file.Files.writeString(
        data.resolve(DesktopConfig.FILE), "port=" + main + "\nwindow.port=" + window + "\n");
    DesktopConfig c = DesktopConfig.load(data);
    c.choosePort();
    // Тот же порт окна при каждом запуске: тема и кеш окна хранятся по адресу с портом.
    assertThat(c.chooseWindowPort()).isEqualTo(window);
    assertThat(DesktopConfig.load(data).chooseWindowPort()).isEqualTo(window);

    try (ServerSocket busy = new ServerSocket(window, 0, InetAddress.getLoopbackAddress())) {
      assertThat(busy.isBound()).isTrue();
      int moved = DesktopConfig.load(data).chooseWindowPort();
      assertThat(moved).isNotEqualTo(window).isNotEqualTo(main);
      assertThat(DesktopConfig.load(data).chooseWindowPort()).isEqualTo(moved);
    }

    // Порт окна совпал с основным (правка руками) – берётся другой.
    java.nio.file.Files.writeString(
        data.resolve(DesktopConfig.FILE), "port=" + main + "\nwindow.port=" + main + "\n");
    assertThat(DesktopConfig.load(data).chooseWindowPort()).isNotEqualTo(main);
  }

  @Test
  void lanSwitchIsPersisted(@TempDir Path data) throws IOException {
    DesktopConfig c = DesktopConfig.load(data);
    assertThat(c.lan()).isFalse();
    assertThat(c.bindAddress()).isEqualTo("127.0.0.1");
    c.setLan(true);
    assertThat(DesktopConfig.load(data).bindAddress()).isEqualTo("0.0.0.0");
  }
}
