package app.groupbase.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.groupbase.ApiClient;
import app.groupbase.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Пользователь группы A не видит и не меняет ничего в группе B. Переопределения прав по группам.
 */
class GroupIsolationIT extends IntegrationTest {

  private static Map<String, Object> oneAccount() {
    return Map.of("accounts", List.of(Map.of("displayName", "Студентов Новый")));
  }

  @Test
  void membersOfOneGroupCannotReachAnother() {
    long a = newGroup("A");
    long b = newGroup("B");
    TestUser studentA = newUser(a, "student");
    TestUser headmanA = newUser(a, "headman");
    newUser(b, "student");

    assertThat(studentA.api().get("/api/groups/" + a + "/members").status()).isEqualTo(200);
    assertThat(studentA.api().get("/api/groups/" + b + "/members").status()).isEqualTo(403);
    assertThat(studentA.api().get("/api/groups/" + b).status()).isEqualTo(403);
    assertThat(headmanA.api().post("/api/groups/" + b + "/invites", Map.of("ttlHours", 1)).status())
        .isEqualTo(403);
    assertThat(headmanA.api().post("/api/groups/" + b + "/accounts", oneAccount()).status())
        .isEqualTo(403);
    assertThat(headmanA.api().get("/api/groups/" + b + "/audit").status()).isEqualTo(403);

    JsonNode groups = studentA.api().get("/api/me").json().get("groups");
    assertThat(groups.size()).isEqualTo(1);
    assertThat(groups.get(0).get("id").asLong()).isEqualTo(a);
    assertThat(studentA.api().get("/api/groups").json().size()).isEqualTo(1);
  }

  /**
   * Аудит 1.0.3: всё содержимое группы B – предметы, задания, новости, материалы, файлы, пары,
   * архивы, реакции и комментарии – недоступно участнику группы A ни на чтение, ни на изменение, и
   * не просачивается в поиск, офлайн-копию и «кто на сайте».
   */
  @Test
  void contentOfAnotherGroupIsNeitherVisibleNorChangeable() {
    long a = newGroup("Изоляция A");
    long b = newGroup("Изоляция B");
    TestUser headmanB = newUser(b, "headman");
    TestUser outsider = newUser(a, "headman");
    ApiClient hb = headmanB.api();
    ApiClient x = outsider.api();

    long subject =
        hb.post("/api/groups/" + b + "/subjects", Map.of("name", "Секретный предмет"))
            .json()
            .get("id")
            .asLong();
    long hw =
        hb.post(
                "/api/homework",
                Map.of(
                    "subjectId",
                    subject,
                    "title",
                    "Секретное задание",
                    "dueAt",
                    clock.millis() + 86_400_000L))
            .json()
            .get("id")
            .asLong();
    long news =
        hb.post(
                "/api/news",
                Map.of("groupIds", List.of(b), "title", "Секретная новость", "body", "текст"))
            .json()
            .get("id")
            .asLong();
    long file =
        hb.upload("секрет.txt", "секретное содержимое".getBytes()).json().get("id").asLong();
    long material =
        hb.post(
                "/api/subjects/" + subject + "/materials",
                Map.of("kind", "file", "fileId", file, "title", "Секретный конспект"))
            .json()
            .get("id")
            .asLong();
    long comment =
        hb.post("/api/news/" + news + "/comments", Map.of("body", "Секретный комментарий"))
            .json()
            .get("id")
            .asLong();
    long semester =
        hb.post("/api/groups/" + b + "/semesters", Map.of("name", "Архив B", "manual", true))
            .json()
            .get("id")
            .asLong();

    // Чтение: ни по номеру, ни списком.
    for (String path :
        List.of(
            "/api/subjects/" + subject,
            "/api/subjects/" + subject + "/materials",
            "/api/homework/" + hw,
            "/api/homework/" + hw + "/comments",
            "/api/news/" + news,
            "/api/news/" + news + "/comments",
            "/api/materials/" + material,
            "/api/files/" + file,
            "/api/files/" + file + "/html",
            "/api/groups/" + b + "/semesters")) {
      assertThat(x.get(path).status()).as(path).isIn(403, 404);
    }
    for (String path :
        List.of(
            "/api/subjects",
            "/api/homework",
            "/api/news",
            "/api/materials/recent",
            "/api/today",
            "/api/sync",
            "/api/search?q=Секретн",
            "/api/presence")) {
      var r = x.get(path);
      assertThat(r.status()).as(path).isEqualTo(200);
      assertThat(r.body())
          .as(path)
          .doesNotContain("Секретный предмет")
          .doesNotContain("Секретное задание")
          .doesNotContain("Секретная новость")
          .doesNotContain("Секретный конспект")
          .doesNotContain("Секретный комментарий")
          .doesNotContain("секретное содержимое")
          .doesNotContain(headmanB.username());
    }

    // Изменение и удаление – тоже нет.
    assertThat(x.patch("/api/subjects/" + subject, Map.of("name", "Взлом")).status())
        .isIn(403, 404);
    assertThat(x.patch("/api/homework/" + hw, Map.of("title", "Взлом")).status()).isIn(403, 404);
    assertThat(x.put("/api/homework/" + hw + "/done", Map.of("value", true)).status())
        .isIn(403, 404);
    assertThat(x.patch("/api/news/" + news, Map.of("title", "Взлом")).status()).isIn(403, 404);
    assertThat(
            x.put("/api/news/" + news + "/reactions", Map.of("emoji", "👍", "value", true))
                .status())
        .isIn(403, 404);
    assertThat(x.post("/api/news/" + news + "/comments", Map.of("body", "чужой")).status())
        .isIn(403, 404);
    assertThat(x.put("/api/comments/" + comment + "/hidden", Map.of("value", true)).status())
        .isIn(403, 404);
    assertThat(x.delete("/api/comments/" + comment).status()).isIn(403, 404);
    assertThat(x.patch("/api/materials/" + material, Map.of("title", "Взлом")).status())
        .isIn(403, 404);
    assertThat(
            x.post(
                    "/api/subjects/" + subject + "/materials",
                    Map.of("kind", "note", "description", "чужое"))
                .status())
        .isIn(403, 404);
    assertThat(x.patch("/api/semesters/" + semester, Map.of("name", "Взлом")).status())
        .isIn(403, 404);
    assertThat(x.post("/api/semesters/" + semester + "/subjects", Map.of("name", "Взлом")).status())
        .isIn(403, 404);
    assertThat(x.delete("/api/semesters/" + semester).status()).isIn(403, 404);
    assertThat(x.delete("/api/materials/" + material).status()).isIn(403, 404);
    assertThat(x.delete("/api/news/" + news).status()).isIn(403, 404);
    assertThat(x.delete("/api/homework/" + hw).status()).isIn(403, 404);
    assertThat(x.delete("/api/subjects/" + subject).status()).isIn(403, 404);
    // Чужой загруженный файл нельзя прицепить к своему: файл должен быть своим.
    long own =
        x.post("/api/groups/" + a + "/subjects", Map.of("name", "Свой")).json().get("id").asLong();
    assertThat(
            x.post(
                    "/api/subjects/" + own + "/materials",
                    Map.of("kind", "file", "fileId", file, "title", "Украденный"))
                .status())
        .isIn(400, 403, 404, 409);

    // Всё на месте.
    assertThat(hb.get("/api/subjects/" + subject).json().get("name").asString())
        .isEqualTo("Секретный предмет");
    assertThat(hb.get("/api/homework/" + hw).status()).isEqualTo(200);
    assertThat(hb.get("/api/news/" + news).status()).isEqualTo(200);
    assertThat(hb.get("/api/files/" + file).status()).isEqualTo(200);
  }

  @Test
  void studentsHaveNoManagementRights() {
    long a = newGroup("Студенты");
    TestUser s = newUser(a, "student");
    assertThat(s.api().post("/api/groups/" + a + "/invites", Map.of("ttlHours", 1)).status())
        .isEqualTo(403);
    assertThat(s.api().post("/api/groups/" + a + "/accounts", oneAccount()).status())
        .isEqualTo(403);
    assertThat(s.api().get("/api/groups/" + a + "/permissions").status()).isEqualTo(403);
    assertThat(s.api().get("/api/admin/users").status()).isEqualTo(403);
    assertThat(s.api().post("/api/groups", Map.of("name", "Своя")).status()).isEqualTo(403);
  }

  @Test
  void groupOverrideWinsOverInstanceOverride() {
    long a = newGroup("Пер-A");
    long b = newGroup("Пер-B");
    TestUser headmanA = newUser(a, "headman");
    TestUser headmanB = newUser(b, "headman");
    TestUser deputyA = newUser(a, "deputy");
    TestUser deputyB = newUser(b, "deputy");
    String path = "/permissions";
    Map<String, Object> cell = Map.of("role", "deputy", "permission", "create_accounts");

    // ⚙ по умолчанию выключено.
    assertThat(deputyA.api().post("/api/groups/" + a + "/accounts", oneAccount()).status())
        .isEqualTo(403);

    // Староста A включает право в своей группе — в группе B оно по-прежнему выключено.
    var on = headmanA.api().put("/api/groups/" + a + path, with(cell, true));
    assertThat(on.status()).isEqualTo(200);
    assertThat(deputyA.api().post("/api/groups/" + a + "/accounts", oneAccount()).status())
        .isEqualTo(200);
    assertThat(deputyB.api().post("/api/groups/" + b + "/accounts", oneAccount()).status())
        .isEqualTo(403);
    assertThat(headmanA.api().put("/api/groups/" + b + path, with(cell, true)).status())
        .isEqualTo(403);

    // Admin включает на уровне инстанса — теперь и в B.
    try {
      assertThat(admin().put("/api/admin/permissions", with(cell, true)).status()).isEqualTo(200);
      assertThat(deputyB.api().post("/api/groups/" + b + "/accounts", oneAccount()).status())
          .isEqualTo(200);
      // Староста B выключает у себя — переопределение группы важнее.
      headmanB.api().put("/api/groups/" + b + path, with(cell, false));
      assertThat(deputyB.api().post("/api/groups/" + b + "/accounts", oneAccount()).status())
          .isEqualTo(403);
      // Сброс переопределения группы возвращает значение инстанса.
      headmanB.api().put("/api/groups/" + b + path, with(cell, null));
      assertThat(deputyB.api().post("/api/groups/" + b + "/accounts", oneAccount()).status())
          .isEqualTo(200);
    } finally {
      admin().put("/api/admin/permissions", with(cell, null));
    }
  }

  @Test
  void onlyConfigurableCellsCanBeChanged() {
    long a = newGroup("Настройки");
    TestUser headman = newUser(a, "headman");
    var r =
        headman
            .api()
            .put(
                "/api/groups/" + a + "/permissions",
                Map.of("role", "student", "permission", "moderate_content", "allowed", true));
    assertThat(r.status()).isEqualTo(400);
    // ⚙: у замов — аккаунты и предметы; у студентов — задания, материалы (включены), предложения
    // и расписание (выключены).
    var list = headman.api().get("/api/groups/" + a + "/permissions").json();
    assertThat(list.size()).isEqualTo(6);
  }

  @Test
  void roleChangesFollowHierarchy() {
    long a = newGroup("Иерархия");
    TestUser headman = newUser(a, "headman");
    TestUser deputy = newUser(a, "deputy");
    TestUser student = newUser(a, "student");
    String roleOf = "/api/groups/" + a + "/members/";

    assertThat(
            headman.api().put(roleOf + student.id() + "/role", Map.of("role", "deputy")).status())
        .isEqualTo(200);
    assertThat(
            headman.api().put(roleOf + student.id() + "/role", Map.of("role", "headman")).status())
        .isEqualTo(403);
    assertThat(
            deputy.api().put(roleOf + student.id() + "/role", Map.of("role", "student")).status())
        .isEqualTo(403);
    assertThat(
            headman.api().put(roleOf + headman.id() + "/role", Map.of("role", "student")).status())
        .isEqualTo(403);
    assertThat(admin().put(roleOf + student.id() + "/role", Map.of("role", "headman")).status())
        .isEqualTo(200);
  }

  @Test
  void blockingRespectsRanksAcrossGroups() {
    long a = newGroup("Ранг-A");
    long b = newGroup("Ранг-B");
    TestUser headmanA = newUser(a, "headman");
    TestUser headmanB = newUser(b, "headman");
    // Староста B ещё и студент в группе A — староста A не может его заблокировать.
    admin().post("/api/groups/" + a + "/invites", Map.of("ttlHours", 1)).json();
    var inv = admin().post("/api/groups/" + a + "/invites", Map.of("ttlHours", 1)).json();
    String t = inv.get("path").asString().substring("/invite/".length());
    headmanB.api().post("/api/invites/" + t + "/join", null);
    assertThat(
            headmanA
                .api()
                .post("/api/groups/" + a + "/members/" + headmanB.id() + "/block", null)
                .status())
        .isEqualTo(403);

    TestUser student = newUser(a, "student");
    assertThat(
            headmanA
                .api()
                .post("/api/groups/" + a + "/members/" + student.id() + "/block", null)
                .status())
        .isEqualTo(200);
    // Участника чужой группы через свою группу заблокировать нельзя.
    TestUser studentB = newUser(b, "student");
    assertThat(
            headmanA
                .api()
                .post("/api/groups/" + a + "/members/" + studentB.id() + "/block", null)
                .status())
        .isEqualTo(404);
  }

  @Test
  void moderatorSeesAllGroupsButCannotBlockOrRemovePeople() {
    long a = newGroup("Модер");
    TestUser mod = newUser(a, "student");
    assertThat(
            admin()
                .put("/api/admin/users/" + mod.id() + "/instance-role", Map.of("role", "moderator"))
                .status())
        .isEqualTo(200);
    // Назначение модератором не выкидывает со всех устройств: прежний вход уже с новой ролью.
    assertThat(mod.api().get("/api/me").json().get("user").get("instanceRole").asString())
        .isEqualTo("moderator");
    var modApi = login(mod.username(), PASSWORD);
    long b = newGroup("Чужая");
    TestUser victim = newUser(b, "student");
    assertThat(modApi.get("/api/groups/" + b + "/members").status()).isEqualTo(200);
    // Блокировать и исключать людей могут только администратор и староста.
    assertThat(modApi.post("/api/admin/users/" + victim.id() + "/block", null).status())
        .isEqualTo(403);
    assertThat(
            modApi.post("/api/groups/" + b + "/members/" + victim.id() + "/block", null).status())
        .isEqualTo(403);
    assertThat(modApi.delete("/api/groups/" + b + "/members/" + victim.id()).status())
        .isEqualTo(403);
    modApi
        .get("/api/me")
        .json()
        .get("groups")
        .forEach(gr -> assertThat(gr.get("permissions").toString()).doesNotContain("block_users"));
    long adminId = admin().get("/api/me").json().get("user").get("id").asLong();
    assertThat(modApi.post("/api/admin/users/" + adminId + "/block", null).status()).isEqualTo(403);
    assertThat(modApi.patch("/api/admin/settings", Map.of("name", "x")).status()).isEqualTo(403);
    assertThat(modApi.post("/api/groups/" + b + "/invites", Map.of("ttlHours", 1)).status())
        .isEqualTo(403);
  }

  @Test
  void progressIsForThoseWhoInvite() {
    long g = newGroup("Шаги");
    TestUser headman = newUser(g, "headman");
    TestUser student = newUser(g, "student");
    var p = headman.api().get("/api/groups/" + g + "/progress");
    assertThat(p.status()).isEqualTo(200);
    assertThat(p.json().get("members").asInt()).isEqualTo(2);
    assertThat(p.json().get("subjects").asInt()).isZero();
    headman.api().post("/api/groups/" + g + "/subjects", Map.of("name", "Физика"));
    assertThat(headman.api().get("/api/groups/" + g + "/progress").json().get("subjects").asInt())
        .isEqualTo(1);
    assertThat(student.api().get("/api/groups/" + g + "/progress").status()).isEqualTo(403);
  }

  @Test
  void onlyHeadmanAndAdminSeeUsernames() {
    long g = newGroup("Логины");
    TestUser headman = newUser(g, "headman");
    TestUser deputy = newUser(g, "deputy");
    TestUser student = newUser(g, "student");
    String path = "/api/groups/" + g + "/members";

    for (ApiClient c : List.of(admin(), headman.api())) {
      var list = c.get(path).json();
      assertThat(list.size()).isEqualTo(3);
      list.forEach(m -> assertThat(m.get("username").isString()).isTrue());
    }
    for (ApiClient c : List.of(deputy.api(), student.api())) {
      var list = c.get(path).json();
      assertThat(list.size()).isEqualTo(3);
      list.forEach(
          m -> {
            assertThat(m.get("username").isNull()).as("логин скрыт").isTrue();
            assertThat(m.get("displayName").asString()).isNotBlank();
          });
    }
  }

  private static Map<String, Object> with(Map<String, Object> cell, Boolean allowed) {
    var m = new java.util.HashMap<>(cell);
    m.put("allowed", allowed);
    return m;
  }
}
