package app.groupbase.content;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Правка комментариев (1.0.4): свой – автор, любой – администратор и староста. */
class CommentEditIT extends IntegrationTest {

  private static JsonNode byId(JsonNode list, long id) {
    for (JsonNode c : list) {
      if (c.get("id").asLong() == id) {
        return c;
      }
    }
    return null;
  }

  @Test
  void ownByAuthorAnyByModeratorsNotByOthers() {
    long g = newGroup("Комментарии");
    TestUser headman = newUser(g, "headman");
    TestUser author = newUser(g, "student");
    TestUser other = newUser(g, "student");
    long news =
        headman
            .api()
            .post("/api/news", Map.of("title", "Собрание", "body", "", "groupIds", List.of(g)))
            .json()
            .get("id")
            .asLong();
    String list = "/api/news/" + news + "/comments";
    long id = author.api().post(list, Map.of("body", "Приду в 5")).json().get("id").asLong();

    // Автору – можно и с исходным текстом; другому студенту – нет.
    JsonNode mine = byId(author.api().get(list).json(), id);
    assertThat(mine.get("canEdit").asBoolean()).isTrue();
    assertThat(mine.get("bodyMd").asString()).isEqualTo("Приду в 5");
    assertThat(mine.get("editedAt").isNull()).isTrue();
    JsonNode theirs = byId(other.api().get(list).json(), id);
    assertThat(theirs.get("canEdit").asBoolean()).isFalse();
    assertThat(theirs.get("bodyMd").isNull()).isTrue();
    assertThat(other.api().patch("/api/comments/" + id, Map.of("body", "взлом")).status())
        .isEqualTo(403);

    var own = author.api().patch("/api/comments/" + id, Map.of("body", "Приду в **6**"));
    assertThat(own.status()).as(own.body()).isEqualTo(200);
    assertThat(own.json().get("bodyHtml").asString()).contains("<strong>6</strong>");
    assertThat(own.json().get("editedAt").isNull()).isFalse();
    assertThat(author.api().patch("/api/comments/" + id, Map.of("body", "  ")).status())
        .isEqualTo(400);

    // Староста и администратор меняют чужой; правка – в журнале.
    assertThat(byId(headman.api().get(list).json(), id).get("canEdit").asBoolean()).isTrue();
    assertThat(
            headman.api().patch("/api/comments/" + id, Map.of("body", "Перенесли на 7")).status())
        .isEqualTo(200);
    assertThat(admin().patch("/api/comments/" + id, Map.of("body", "Собрание в 7")).status())
        .isEqualTo(200);
    JsonNode now = byId(other.api().get(list).json(), id);
    assertThat(now.get("bodyHtml").asString()).contains("Собрание в 7");
    assertThat(now.get("editedAt").isNull()).isFalse();
    String audit = admin().get("/api/groups/" + g + "/audit").body();
    assertThat(audit).contains("comment.edit");
  }
}
