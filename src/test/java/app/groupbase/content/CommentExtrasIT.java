package app.groupbase.content;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Комментарии (1.0.4): реакции, включая несогласие, и картинки с гифками. */
class CommentExtrasIT extends IntegrationTest {

  private static byte[] png() throws IOException {
    BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
    var out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private static final byte[] GIF = {
    'G',
    'I',
    'F',
    '8',
    '9',
    'a',
    1,
    0,
    1,
    0,
    (byte) 0x80,
    0,
    0,
    0,
    0,
    0,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    '!',
    (byte) 0xf9,
    4,
    1,
    0,
    0,
    0,
    0,
    ',',
    0,
    0,
    0,
    0,
    1,
    0,
    1,
    0,
    0,
    2,
    2,
    'D',
    1,
    0,
    ';'
  };

  private static JsonNode byId(JsonNode list, long id) {
    for (JsonNode c : list) {
      if (c.get("id").asLong() == id) {
        return c;
      }
    }
    return null;
  }

  @Test
  void picturesAndGifsAreAttachedAndVisibleOnlyWithTheComment() throws IOException {
    long g = newGroup("Мемы");
    long stranger = newGroup("Чужие");
    TestUser headman = newUser(g, "headman");
    TestUser author = newUser(g, "student");
    TestUser mate = newUser(g, "student");
    TestUser outsider = newUser(stranger, "student");
    long news =
        headman
            .api()
            .post("/api/news", Map.of("title", "Мемы недели", "body", "", "groupIds", List.of(g)))
            .json()
            .get("id")
            .asLong();
    String list = "/api/news/" + news + "/comments";
    long pic = author.api().upload("мем.png", png()).json().get("id").asLong();
    var gif = author.api().upload("кот.gif", GIF);
    assertThat(gif.status()).as(gif.body()).isEqualTo(200);
    long gifId = gif.json().get("id").asLong();

    // Только картинки – без текста можно.
    var c = author.api().post(list, Map.of("body", "", "files", List.of(pic, gifId)));
    assertThat(c.status()).as(c.body()).isEqualTo(200);
    long id = c.json().get("id").asLong();
    assertThat(c.json().get("files").size()).isEqualTo(2);
    assertThat(c.json().get("files").get(1).get("mime").asString()).isEqualTo("image/gif");

    assertThat(mate.api().download("/api/files/" + gifId).statusCode()).isEqualTo(200);
    assertThat(outsider.api().download("/api/files/" + gifId).statusCode()).isEqualTo(404);

    // Чужой файл, уже прикреплённый и не картинка – нельзя.
    long matePic = mate.api().upload("мой.png", png()).json().get("id").asLong();
    assertThat(author.api().post(list, Map.of("body", "x", "files", List.of(matePic))).status())
        .isEqualTo(403);
    assertThat(author.api().post(list, Map.of("body", "x", "files", List.of(pic))).status())
        .isEqualTo(403);
    long pdf =
        author
            .api()
            .upload("конспект.pdf", "%PDF-1.4\n%%EOF\n".getBytes())
            .json()
            .get("id")
            .asLong();
    assertThat(author.api().post(list, Map.of("body", "x", "files", List.of(pdf))).status())
        .isEqualTo(400);
    // Без текста и без картинок – нельзя.
    assertThat(author.api().post(list, Map.of("body", " ")).status()).isEqualTo(400);

    // Скрытый комментарий – и картинка в нём – не видны другим.
    headman.api().put("/api/comments/" + id + "/hidden", Map.of("value", true));
    assertThat(mate.api().download("/api/files/" + pic).statusCode()).isEqualTo(404);
    assertThat(author.api().download("/api/files/" + pic).statusCode()).isEqualTo(200);
  }

  @Test
  void reactionsIncludeDisagreement() {
    long g = newGroup("Реакции");
    TestUser headman = newUser(g, "headman");
    TestUser author = newUser(g, "student");
    TestUser mate = newUser(g, "student");
    long hw =
        headman
            .api()
            .post("/api/news", Map.of("title", "Перенос пары", "body", "", "groupIds", List.of(g)))
            .json()
            .get("id")
            .asLong();
    String list = "/api/news/" + hw + "/comments";
    long id =
        author.api().post(list, Map.of("body", "А можно в пятницу?")).json().get("id").asLong();

    var down =
        mate.api().put("/api/comments/" + id + "/reactions", Map.of("emoji", "👎", "value", true));
    assertThat(down.status()).as(down.body()).isEqualTo(200);
    assertThat(down.json().get(0).get("emoji").asString()).isEqualTo("👎");
    assertThat(down.json().get(0).get("mine").asBoolean()).isTrue();
    headman.api().put("/api/comments/" + id + "/reactions", Map.of("emoji", "🤡", "value", true));
    assertThat(
            mate.api()
                .put("/api/comments/" + id + "/reactions", Map.of("emoji", "🍕", "value", true))
                .status())
        .isEqualTo(400);

    JsonNode seen = byId(author.api().get(list).json(), id);
    assertThat(seen.get("reactions").size()).isEqualTo(2);
    assertThat(seen.get("reactions").get(0).get("mine").asBoolean()).isFalse();

    mate.api().put("/api/comments/" + id + "/reactions", Map.of("emoji", "👎", "value", false));
    assertThat(byId(author.api().get(list).json(), id).get("reactions").size()).isEqualTo(1);
    // Новости тоже знают несогласие.
    assertThat(
            mate.api()
                .put("/api/news/" + hw + "/reactions", Map.of("emoji", "👎", "value", true))
                .status())
        .isEqualTo(200);
  }
}
