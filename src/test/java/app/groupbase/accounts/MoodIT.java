package app.groupbase.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Статус «для себя» (1.0.4): своя строка под именем, видна участникам группы. */
class MoodIT extends IntegrationTest {

  @Test
  void moodIsSetShownToGroupAndCleared() {
    long g = newGroup("Статусы");
    TestUser me = newUser(g, "student");
    TestUser mate = newUser(g, "student");

    var set = me.api().put("/api/me/mood", Map.of("text", "  сплю на паре 😴\n  не будить "));
    assertThat(set.status()).as(set.body()).isEqualTo(200);
    assertThat(set.json().get("mood").asString()).isEqualTo("сплю на паре 😴 не будить");
    assertThat(me.api().get("/api/me").json().get("user").get("mood").asString())
        .isEqualTo("сплю на паре 😴 не будить");

    var members = mate.api().get("/api/groups/" + g + "/members").json();
    boolean seen = false;
    for (var m : members) {
      if (m.get("userId").asLong() == me.id()) {
        assertThat(m.get("mood").asString()).isEqualTo("сплю на паре 😴 не будить");
        seen = true;
      }
    }
    assertThat(seen).isTrue();

    // 80 знаков (эмодзи – один знак) – можно, 81 – нет.
    assertThat(me.api().put("/api/me/mood", Map.of("text", "🙂".repeat(80))).status())
        .isEqualTo(200);
    assertThat(me.api().put("/api/me/mood", Map.of("text", "x".repeat(81))).status())
        .isEqualTo(400);

    assertThat(me.api().put("/api/me/mood", Map.of("text", " ")).status()).isEqualTo(200);
    assertThat(me.api().get("/api/me").json().get("user").get("mood").isNull()).isTrue();
  }
}
