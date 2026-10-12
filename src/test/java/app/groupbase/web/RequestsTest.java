package app.groupbase.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestsTest {

  private static boolean local(String ip) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRemoteAddr(ip);
    return Requests.fromThisComputer(req);
  }

  @Test
  void onlyLoopbackCountsAsThisComputer() {
    assertThat(local("127.0.0.1")).isTrue();
    assertThat(local("::1")).isTrue();
    assertThat(local("0:0:0:0:0:0:0:1")).isTrue();
    assertThat(local("192.168.1.10")).isFalse();
    assertThat(local("10.0.0.2")).isFalse();
    assertThat(local("2001:db8::1")).isFalse();
  }

  @Test
  void namesAndGarbageAreNotResolved() {
    // Имя вместо адреса не ищем в DNS: «localhost» подставить нельзя.
    assertThat(local("localhost")).isFalse();
    assertThat(local("")).isFalse();
    assertThat(local("evil.example")).isFalse();
  }

  @Test
  void withAWindowPortOnlyThatPortIsThisComputer() {
    // Туннельный клиент подключается к основному порту с 127.0.0.1 – это не окно хоста.
    Requests.windowPort(40123);
    try {
      MockHttpServletRequest tunnel = new MockHttpServletRequest();
      tunnel.setRemoteAddr("127.0.0.1");
      tunnel.setLocalPort(8080);
      assertThat(Requests.fromThisComputer(tunnel)).isFalse();
      MockHttpServletRequest window = new MockHttpServletRequest();
      window.setRemoteAddr("127.0.0.1");
      window.setLocalPort(40123);
      assertThat(Requests.fromThisComputer(window)).isTrue();
    } finally {
      Requests.windowPort(0);
    }
  }
}
