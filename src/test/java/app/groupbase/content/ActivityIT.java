package app.groupbase.content;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Рейтинг активности группы (1.0.4). */
class ActivityIT extends IntegrationTest {

  private static JsonNode row(JsonNode board, long userId) {
    for (JsonNode r : board.get("rows")) {
      if (r.get("userId").asLong() == userId) {
        return r;
      }
    }
    return null;
  }

  @Test
  void pointsForVisibleContributionsOnlyWithinTheGroup() {
    long g = newGroup("Рейтинг");
    long other = newGroup("Соседи");
    TestUser headman = newUser(g, "headman");
    TestUser student = newUser(g, "student");
    TestUser quiet = newUser(g, "student");
    TestUser stranger = newUser(other, "student");
    long subject =
        admin()
            .post("/api/groups/" + g + "/subjects", Map.of("name", "Физика " + uniq()))
            .json()
            .get("id")
            .asLong();

    // Студент: материал (3) + задание (3) + комментарий (1) = 7.
    assertThat(
            student
                .api()
                .post(
                    "/api/subjects/" + subject + "/materials",
                    Map.of("kind", "link", "title", "Конспект", "url", "https://example.org/a"))
                .status())
        .isEqualTo(200);
    long hw =
        student
            .api()
            .post(
                "/api/homework",
                Map.of(
                    "subjectId", subject, "title", "Задачи", "dueAt", clock.millis() + 86_400_000L))
            .json()
            .get("id")
            .asLong();
    // Староста: новость (2) + реакция (без очков).
    long news =
        headman
            .api()
            .post(
                "/api/news", Map.of("title", "Пара перенесена", "body", "", "groupIds", List.of(g)))
            .json()
            .get("id")
            .asLong();
    assertThat(
            student
                .api()
                .post("/api/news/" + news + "/comments", Map.of("body", "Спасибо"))
                .status())
        .isEqualTo(200);
    assertThat(
            headman
                .api()
                .put("/api/news/" + news + "/reactions", Map.of("emoji", "👍", "value", true))
                .status())
        .isEqualTo(200);
    assertThat(student.api().put("/api/homework/" + hw + "/done", Map.of("value", true)).status())
        .isEqualTo(200);
    // Скрытая модератором новость не считается.
    long hidden =
        headman
            .api()
            .post("/api/news", Map.of("title", "Черновик", "body", "", "groupIds", List.of(g)))
            .json()
            .get("id")
            .asLong();
    assertThat(admin().put("/api/news/" + hidden + "/hidden", Map.of("value", true)).status())
        .isEqualTo(200);

    String url = "/api/groups/" + g + "/activity";
    var r = student.api().get(url);
    assertThat(r.status()).as(r.body()).isEqualTo(200);
    JsonNode board = r.json();
    assertThat(board.get("period").asString()).isEqualTo("month");
    JsonNode s = row(board, student.id());
    assertThat(s.get("points").asInt()).isEqualTo(7);
    assertThat(s.get("materials").asInt()).isEqualTo(1);
    assertThat(s.get("homework").asInt()).isEqualTo(1);
    assertThat(s.get("comments").asInt()).isEqualTo(1);
    assertThat(s.get("place").asInt()).isEqualTo(1);
    assertThat(s.get("days").asInt()).isEqualTo(1);
    JsonNode h = row(board, headman.id());
    assertThat(h.get("points").asInt()).isEqualTo(2);
    assertThat(h.get("news").asInt()).isEqualTo(1);
    assertThat(h.get("reactions").asInt()).isEqualTo(1);
    assertThat(h.get("place").asInt()).isEqualTo(2);
    // Без вклада – не в таблице; «сделано» – только своё.
    assertThat(row(board, quiet.id())).isNull();
    assertThat(board.get("myDone").asInt()).isEqualTo(1);
    assertThat(headman.api().get(url).json().get("myDone").asInt()).isZero();

    assertThat(student.api().get(url + "?period=week").json().get("period").asString())
        .isEqualTo("week");
    assertThat(student.api().get(url + "?period=all").json().get("from").asLong()).isZero();
    // Чужая группа рейтинг не видит.
    assertThat(stranger.api().get(url).status()).isEqualTo(403);
  }

  @Test
  void periodsAndPoints() {
    assertThat(Activity.period("WEEK")).isEqualTo("week");
    assertThat(Activity.period("год")).isEqualTo("month");
    assertThat(Activity.period(null)).isEqualTo("month");
    assertThat(Activity.from("all", 1_000_000_000L)).isZero();
    assertThat(Activity.from("week", 1_000_000_000L)).isEqualTo(1_000_000_000L - 7 * 86_400_000L);
    assertThat(Activity.points(1, 1, 1, 1)).isEqualTo(9);
  }
}
