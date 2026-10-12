package app.groupbase.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Сетевые настройки приложения хоста, нужные до старта сервера: порт и режим «локальная сеть».
 * Лежат в {@code desktop.properties} каталога данных. Порт постоянный: у окна хоста адрес
 * http://127.0.0.1:порт, и от него зависят сохранённые в окне данные. Порт окна хоста (1.0.3,
 * только 127.0.0.1) – тоже постоянный по той же причине: тема, оформление и кеш окна живут по
 * адресу с портом.
 */
public final class DesktopConfig {

  public static final String FILE = "desktop.properties";
  public static final int DEFAULT_PORT = 17380;
  public static final int DEFAULT_WINDOW_PORT = 17381;

  private final Path file;
  private int port;
  private int windowPort;
  private boolean lan;

  private DesktopConfig(Path file, int port, int windowPort, boolean lan) {
    this.file = file;
    this.port = port;
    this.windowPort = windowPort;
    this.lan = lan;
  }

  public static DesktopConfig load(Path dataDir) throws IOException {
    Path f = dataDir.resolve(FILE);
    Properties p = new Properties();
    if (Files.isRegularFile(f)) {
      try (InputStream in = Files.newInputStream(f)) {
        p.load(in);
      }
    }
    return new DesktopConfig(
        f,
        number(p, "port", DEFAULT_PORT),
        number(p, "window.port", DEFAULT_WINDOW_PORT),
        Boolean.parseBoolean(p.getProperty("lan", "false")));
  }

  private static int number(Properties p, String key, int fallback) {
    try {
      return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  public int port() {
    return port;
  }

  public boolean lan() {
    return lan;
  }

  public String bindAddress() {
    return lan ? "0.0.0.0" : "127.0.0.1";
  }

  public void setLan(boolean lan) throws IOException {
    this.lan = lan;
    save();
  }

  /**
   * Порт, на котором сайт работал на другом компьютере хоста: адрес в CloudPub ведёт на него.
   * Берём, если он свободен; иначе остаётся прежний.
   */
  public void prefer(int p) throws IOException {
    if (p != port && p >= 1024 && p <= 65535 && free(p)) {
      port = p;
      save();
    }
  }

  /** Сохранённый порт, если он свободен; иначе любой свободный — и он запоминается. */
  public int choosePort() throws IOException {
    if (port < 1024 || port > 65535 || !free(port)) {
      try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
        port = s.getLocalPort();
      }
      save();
    } else if (!Files.exists(file)) {
      save();
    }
    return port;
  }

  /**
   * Порт окна хоста: сохранённый, если свободен на 127.0.0.1 и не совпадает с основным; иначе любой
   * свободный – и он запоминается. Вызывать после {@link #choosePort()}.
   */
  public int chooseWindowPort() throws IOException {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    if (windowPort < 1024
        || windowPort > 65535
        || windowPort == port
        || !free(loopback, windowPort)) {
      do {
        try (ServerSocket s = new ServerSocket(0, 0, loopback)) {
          windowPort = s.getLocalPort();
        }
      } while (windowPort == port);
      save();
    } else if (!Files.exists(file)) {
      save();
    }
    return windowPort;
  }

  private boolean free(int p) {
    try {
      return free(InetAddress.getByName(bindAddress()), p);
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean free(InetAddress address, int p) {
    try (ServerSocket s = new ServerSocket()) {
      // Как Tomcat: порт, на котором только что работал прежний сервер (TIME_WAIT), свободен.
      s.setReuseAddress(true);
      s.bind(new InetSocketAddress(address, p));
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void save() throws IOException {
    Properties p = new Properties();
    p.setProperty("port", String.valueOf(port));
    p.setProperty("window.port", String.valueOf(windowPort));
    p.setProperty("lan", String.valueOf(lan));
    Files.createDirectories(file.getParent());
    try (OutputStream out = Files.newOutputStream(file)) {
      p.store(out, "groupbase: сеть приложения хоста");
    }
  }
}
