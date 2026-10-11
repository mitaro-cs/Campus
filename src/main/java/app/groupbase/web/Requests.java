package app.groupbase.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Мелочи про текущий HTTP-запрос. */
public final class Requests {

  private Requests() {}

  /** Порт окна хоста; 0 – его нет (обычный сервер), тогда «с этого компьютера» – по адресу. */
  private static volatile int windowPort;

  public static void windowPort(int port) {
    windowPort = port;
  }

  /**
   * Запрос пришёл с этого же компьютера напрямую, а не через прокси или туннель. Адрес клиента уже
   * заменён на настоящий по X-Forwarded-For (только от доверенных локальных прокси), поэтому запрос
   * участника через туннель сюда не попадает.
   */
  public static boolean fromThisComputer(HttpServletRequest req) {
    // С отдельным портом окна (1.0.2) – только он: туннельный клиент на этом же компьютере
    // подключается к основному порту с 127.0.0.1, и адрес сам по себе ничего не доказывает.
    int window = windowPort;
    if (window > 0 && req.getLocalPort() != window) {
      return false;
    }
    String ip = req.getRemoteAddr();
    if (ip == null || ip.isEmpty() || !(Character.isDigit(ip.charAt(0)) || ip.indexOf(':') >= 0)) {
      return false;
    }
    try {
      return InetAddress.getByName(ip).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  public static HttpServletRequest current() {
    return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
        .getRequest();
  }
}
