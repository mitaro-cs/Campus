package app.groupbase.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Пары подгрупп (1.0.4): у «№2» – свои и общие пары предмета, пары «№1» – нет. */
class SubgroupLessonsIT extends IntegrationTest {

  private static final DateTimeFormatter UTC =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private static String event(String uid, Instant start, String title) {
    return String.join(
        "\r\n",
        "BEGIN:VEVENT",
        "UID:" + uid,
        "DTSTART:" + UTC.format(start),
        "DTEND:" + UTC.format(start.plus(Duration.ofMinutes(90))),
        "SUMMARY:" + title,
        "END:VEVENT");
  }

  private static List<String> titles(JsonNode lessons) {
    List<String> out = new ArrayList<>();
    lessons.forEach(l -> out.add(l.get("title").asString()));
    return out;
  }

  @Test
  void secondSubgroupSeesCommonLessonsButNotTheFirstOnes() {
    long g = newGroup("Подгруппы и пары");
    TestUser headman = newUser(g, "headman");
    long lang =
        headman
            .api()
            .post("/api/groups/" + g + "/subjects", Map.of("name", "Иностранный язык"))
            .json()
            .get("id")
            .asLong();
    var split = headman.api().post("/api/subjects/" + lang + "/subgroups", Map.of("count", 2));
    assertThat(split.status()).as(split.body()).isEqualTo(200);
    long first = split.json().get(0).get("id").asLong();
    long second = split.json().get(1).get("id").asLong();

    Instant day = clock.instant().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS);
    String ics =
        String.join(
            "\r\n",
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            event("common@vuz", day.plus(Duration.ofHours(9)), "Иностранный язык"),
            event("one@vuz", day.plus(Duration.ofHours(11)), "Иностранный язык (1 подгр.)"),
            event("two@vuz", day.plus(Duration.ofHours(13)), "Иностранный язык (2 подгр.)"),
            event("other@vuz", day.plus(Duration.ofHours(15)), "Физика"),
            "END:VCALENDAR");
    var imported =
        headman
            .api()
            .post(
                "/api/groups/" + g + "/schedule/import", Map.of("ics", ics, "choices", List.of()));
    assertThat(imported.status()).as(imported.body()).isEqualTo(200);

    long from = day.toEpochMilli() - 86_400_000L;
    String range = "&from=" + from + "&to=" + (from + 5 * 86_400_000L);
    JsonNode mine = headman.api().get("/api/schedule?subject=" + second + range).json();
    assertThat(titles(mine)).containsExactly("Иностранный язык", "Иностранный язык (2 подгр.)");
    assertThat(titles(headman.api().get("/api/schedule?subject=" + first + range).json()))
        .containsExactly("Иностранный язык", "Иностранный язык (1 подгр.)");
    // У «№2» есть пары – значит, и вкладка «Расписание», и выбор пары в форме задания.
    assertThat(headman.api().get("/api/subjects/" + second).json().get("lessons").asInt())
        .isEqualTo(2);

    long common = mine.get(0).get("id").asLong();
    long firstOnly = 0;
    for (JsonNode l : headman.api().get("/api/schedule?subject=" + first + range).json()) {
      if (l.get("title").asString().contains("1 подгр")) {
        firstOnly = l.get("id").asLong();
      }
    }
    long due = day.plus(Duration.ofHours(9)).toEpochMilli();
    var ok =
        headman
            .api()
            .post(
                "/api/homework",
                Map.of("subjectId", second, "title", "Text C", "dueAt", due, "lessonId", common));
    assertThat(ok.status()).as(ok.body()).isEqualTo(200);
    assertThat(
            headman
                .api()
                .post(
                    "/api/homework",
                    Map.of("subjectId", second, "title", "x", "dueAt", due, "lessonId", firstOnly))
                .status())
        .isEqualTo(400);
  }
}
