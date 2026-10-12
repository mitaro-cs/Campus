package app.groupbase.web;

import app.groupbase.config.GroupbaseProperties;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Второй вход Tomcat – только для окна хоста (1.0.3): 127.0.0.1 и свой порт. Туннель и локальная
 * сеть ведут на основной порт, поэтому запрос сюда действительно пришёл с этого компьютера.
 */
@Component
class WindowConnector implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

  private final GroupbaseProperties props;

  WindowConnector(GroupbaseProperties props) {
    this.props = props;
  }

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    int port = props.desktop().enabled() ? props.desktop().windowPort() : 0;
    Requests.windowPort(port);
    if (port <= 0) {
      return;
    }
    Connector c = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
    c.setPort(port);
    c.setProperty("address", "127.0.0.1");
    factory.addAdditionalConnectors(c);
  }
}
