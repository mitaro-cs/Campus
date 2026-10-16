package app.groupbase.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Реакции (1.0.2 – новости, 1.0.4 – и комментарии): набор, порядок и сводка «сколько, моя ли, кто».
 * Таблицы одинаковые: {@code post_reactions (post_id …)} и {@code comment_reactions (comment_id
 * …)}.
 */
public final class Reactions {

  /**
   * Какие реакции бывают – и в каком порядке их показывать. С 1.0.4 – и несогласие (👎, 🤡, 😢),
   * просьба владельца.
   */
  public static final List<String> ALL =
      List.of("👍", "👎", "❤️", "🔥", "😂", "😮", "😢", "🤡", "❓");

  static final int WHO_MAX = 30;

  /** Таблица реакций и колонка записи, к которой они. */
  enum Table {
    POST("post_reactions", "post_id"),
    COMMENT("comment_reactions", "comment_id");

    final String table;
    final String column;

    Table(String table, String column) {
      this.table = table;
      this.column = column;
    }
  }

  private Reactions() {}

  static void check(String emoji) {
    if (emoji == null || !ALL.contains(emoji)) {
      throw app.groupbase.web.ApiException.invalid("emoji", "Реакция: " + String.join(" ", ALL));
    }
  }

  static void set(
      JdbcClient db, Table t, long id, long userId, String emoji, boolean on, long now) {
    check(emoji);
    if (on) {
      db.sql(
              "INSERT OR IGNORE INTO "
                  + t.table
                  + " ("
                  + t.column
                  + ", user_id, emoji, at) VALUES (?, ?, ?, ?)")
          .params(id, userId, emoji, now)
          .update();
    } else {
      db.sql("DELETE FROM " + t.table + " WHERE " + t.column + " = ? AND user_id = ? AND emoji = ?")
          .params(id, userId, emoji)
          .update();
    }
  }

  private record Row(long id, String emoji, long userId, String name) {}

  /** Сводка реакций по записям: только те, у кого они есть. */
  static Map<Long, List<NewsService.Reaction>> load(
      JdbcClient db, Table t, List<Long> ids, long me) {
    Map<Long, List<NewsService.Reaction>> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    List<Row> rows =
        db.sql(
                "SELECT r."
                    + t.column
                    + ", r.emoji, r.user_id, u.display_name FROM "
                    + t.table
                    + " r JOIN users u ON u.id = r.user_id WHERE r."
                    + t.column
                    + " IN (:ids) AND u.deleted_at IS NULL ORDER BY r.at, r.user_id")
            .param("ids", ids)
            .query(
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)))
            .list();
    Map<Long, Map<String, List<Row>>> grouped = new HashMap<>();
    for (Row r : rows) {
      grouped
          .computeIfAbsent(r.id(), k -> new HashMap<>())
          .computeIfAbsent(r.emoji(), k -> new ArrayList<>())
          .add(r);
    }
    grouped.forEach(
        (id, byEmoji) -> {
          List<NewsService.Reaction> list = new ArrayList<>();
          for (String e : ALL) {
            List<Row> rs = byEmoji.get(e);
            if (rs == null) {
              continue;
            }
            list.add(
                new NewsService.Reaction(
                    e,
                    rs.size(),
                    rs.stream().anyMatch(x -> x.userId() == me),
                    rs.stream().limit(WHO_MAX).map(Row::name).toList()));
          }
          out.put(id, list);
        });
    return out;
  }
}
