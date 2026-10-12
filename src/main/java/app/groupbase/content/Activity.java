package app.groupbase.content;

import app.groupbase.config.GroupbaseProperties;
import app.groupbase.store.GroupStore;
import app.groupbase.store.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Рейтинг активности группы (1.0.4): кто больше всех делает для группы за неделю, месяц или всё
 * время. Очки – за вклад, который видят все: материал и задание – 3, новость – 2, комментарий – 1.
 * Реакции и дни активности – статистика без очков. Только видимое группе: скрытое модератором и
 * материалы на проверке не считаются. Отметки «сделано» – личное: в ответе только свои.
 */
@Service
public class Activity {

  static final int MATERIAL = 3;
  static final int HOMEWORK = 3;
  static final int NEWS = 2;
  static final int COMMENT = 1;

  public record Row(
      long userId,
      String displayName,
      String avatar,
      String role,
      int place,
      int points,
      int materials,
      int homework,
      int news,
      int comments,
      int reactions,
      int days,
      long lastAt) {}

  /** Таблица за период; {@code myDone} – сколько заданий отметил выполненными сам спрашивающий. */
  public record Board(String period, long from, int people, List<Row> rows, int myDone) {}

  private final JdbcClient db;
  private final GroupStore groups;
  private final Clock clock;
  private final ZoneId zone;

  public Activity(JdbcClient db, GroupStore groups, Clock clock, GroupbaseProperties props) {
    this.db = db;
    this.groups = groups;
    this.clock = clock;
    this.zone = props.timezone();
  }

  /** Начало периода: неделя и месяц – скользящие (7 и 30 дней), «всё время» – 0. */
  static long from(String period, long now) {
    return switch (period) {
      case "week" -> now - Duration.ofDays(7).toMillis();
      case "all" -> 0;
      default -> now - Duration.ofDays(30).toMillis();
    };
  }

  static String period(String raw) {
    String p = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    return p.equals("week") || p.equals("all") ? p : "month";
  }

  /** Сумма очков – одна формула и для сервера, и для подписи «как считаются очки». */
  static int points(int materials, int homework, int news, int comments) {
    return materials * MATERIAL + homework * HOMEWORK + news * NEWS + comments * COMMENT;
  }

  public Board board(long userId, long groupId, String rawPeriod) {
    String period = period(rawPeriod);
    long from = from(period, clock.millis());
    Map<String, Object> p = Map.of("g", groupId, "from", from);

    // Вклад: (кто, вид, когда) – одной выборкой, дальше считаем здесь.
    record Act(long user, String kind, long at) {}
    List<Act> acts =
        db.sql(
                """
                SELECT m.author_id AS who, 'material' AS kind, m.created_at AS at FROM materials m
                WHERE m.created_at >= :from AND m.hidden = 0 AND m.status = 'published'
                  AND m.author_id IS NOT NULL
                  AND EXISTS (SELECT 1 FROM subject_groups sg
                              WHERE sg.subject_id = m.subject_id AND sg.group_id = :g)
                UNION ALL
                SELECT h.author_id, 'homework', h.created_at FROM homework h
                JOIN homework_targets t ON t.homework_id = h.id AND t.group_id = :g
                WHERE h.created_at >= :from AND h.hidden = 0 AND h.author_id IS NOT NULL
                UNION ALL
                SELECT p.author_id, 'news', p.created_at FROM posts p
                JOIN post_targets t ON t.post_id = p.id AND t.group_id = :g
                WHERE p.created_at >= :from AND p.hidden = 0 AND p.author_id IS NOT NULL
                UNION ALL
                SELECT c.author_id, 'comment', c.created_at FROM comments c
                WHERE c.created_at >= :from AND c.hidden = 0 AND c.author_id IS NOT NULL
                  AND ((c.target_type = 'post' AND EXISTS (
                          SELECT 1 FROM post_targets t
                          WHERE t.post_id = c.target_id AND t.group_id = :g))
                    OR (c.target_type = 'homework' AND EXISTS (
                          SELECT 1 FROM homework_targets t
                          WHERE t.homework_id = c.target_id AND t.group_id = :g))
                    OR (c.target_type = 'material' AND EXISTS (
                          SELECT 1 FROM materials m JOIN subject_groups sg
                            ON sg.subject_id = m.subject_id AND sg.group_id = :g
                          WHERE m.id = c.target_id)))
                UNION ALL
                SELECT r.user_id, 'reaction', r.at FROM post_reactions r
                JOIN post_targets t ON t.post_id = r.post_id AND t.group_id = :g
                WHERE r.at >= :from
                """)
            .params(p)
            .query((rs, i) -> new Act(rs.getLong(1), rs.getString(2), rs.getLong(3)))
            .list();

    record Tally(int[] n, Set<LocalDate> days, long[] last) {}
    Map<Long, Tally> by = new HashMap<>();
    for (Act a : acts) {
      Tally t =
          by.computeIfAbsent(a.user(), k -> new Tally(new int[5], new HashSet<>(), new long[1]));
      t.n()[
          switch (a.kind()) {
            case "material" -> 0;
            case "homework" -> 1;
            case "news" -> 2;
            case "comment" -> 3;
            default -> 4;
          }]++;
      t.days().add(Instant.ofEpochMilli(a.at()).atZone(zone).toLocalDate());
      t.last()[0] = Math.max(t.last()[0], a.at());
    }

    List<Member> members = groups.members(groupId);
    List<Row> rows = new ArrayList<>();
    for (Member m : members) {
      Tally t = by.get(m.userId());
      if (t == null) {
        continue;
      }
      int[] n = t.n();
      int pts = points(n[0], n[1], n[2], n[3]);
      if (pts == 0 && n[4] == 0) {
        continue;
      }
      rows.add(
          new Row(
              m.userId(),
              m.displayName(),
              m.avatar(),
              m.role().name().toLowerCase(Locale.ROOT),
              0,
              pts,
              n[0],
              n[1],
              n[2],
              n[3],
              n[4],
              t.days().size(),
              t.last()[0]));
    }
    rows.sort(
        Comparator.comparingInt(Row::points)
            .reversed()
            .thenComparing(Comparator.comparingInt(Row::days).reversed())
            .thenComparing(Row::displayName));
    // Места с одинаковыми очками – одинаковые (1, 2, 2, 4).
    List<Row> placed = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      Row r = rows.get(i);
      int place =
          i > 0 && r.points() == rows.get(i - 1).points() ? placed.get(i - 1).place() : i + 1;
      placed.add(
          new Row(
              r.userId(),
              r.displayName(),
              r.avatar(),
              r.role(),
              place,
              r.points(),
              r.materials(),
              r.homework(),
              r.news(),
              r.comments(),
              r.reactions(),
              r.days(),
              r.lastAt()));
    }

    int myDone =
        db.sql(
                """
                SELECT COUNT(*) FROM homework_done d
                JOIN homework_targets t ON t.homework_id = d.homework_id AND t.group_id = :g
                WHERE d.user_id = :me AND d.done_at >= :from
                """)
            .param("g", groupId)
            .param("me", userId)
            .param("from", from)
            .query(Integer.class)
            .single();
    return new Board(period, from, members.size(), placed, myDone);
  }
}
