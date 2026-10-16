package app.groupbase.content;

import app.groupbase.store.Rows;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Пары предмета с учётом подгрупп (1.0.4). У обычного предмета – его пары. У подгруппы
 * («Иностранный язык №2») – свои и общие пары предмета: без номера подгруппы в названии из файла.
 * Такие при делении остаются у «№1», а загруженные позже – без предмета (название одинаково похоже
 * на все подгруппы, и расписание не угадывает). Пары чужой подгруппы («… (1 подгр.)») – нет. Нужно,
 * чтобы у «№2» можно было выбрать пару в форме задания и видеть свои пары на странице предмета.
 */
public final class SubgroupLessons {

  private SubgroupLessons() {}

  private record Subject(long id, String name) {}

  private record Lesson(long id, Long subjectId, String title) {}

  /** Пары предмета в этих группах (подходящие – см. выше). */
  public static List<Long> ids(JdbcClient db, long subjectId, Collection<Long> groupIds) {
    if (groupIds.isEmpty()) {
      return List.of();
    }
    String name =
        db.sql("SELECT name FROM subjects WHERE id = ?")
            .param(subjectId)
            .query(String.class)
            .optional()
            .orElse(null);
    if (name == null) {
      return List.of();
    }
    int mine = SubjectService.subgroupOf(name);
    if (mine == 0) {
      return db.sql("SELECT id FROM lessons WHERE subject_id = :s AND group_id IN (:g)")
          .param("s", subjectId)
          .param("g", groupIds)
          .query(Long.class)
          .list();
    }
    String base = key(base(name));
    // Подгруппы того же предмета в этих группах: «№1», «№2»… с тем же названием до номера.
    List<Long> family = new ArrayList<>();
    for (Subject s :
        db.sql(
                """
                SELECT DISTINCT s.id, s.name FROM subjects s
                JOIN subject_groups sg ON sg.subject_id = s.id
                WHERE sg.group_id IN (:g)
                """)
            .param("g", groupIds)
            .query((rs, i) -> new Subject(rs.getLong(1), rs.getString(2)))
            .list()) {
      if (s.id() == subjectId
          || (SubjectService.subgroupOf(s.name()) > 0 && key(base(s.name())).equals(base))) {
        family.add(s.id());
      }
    }
    List<Long> out = new ArrayList<>();
    for (Lesson l :
        db.sql(
                """
                SELECT id, subject_id, title FROM lessons
                WHERE group_id IN (:g) AND (subject_id IN (:f) OR subject_id IS NULL)
                ORDER BY starts_at, id
                """)
            .param("g", groupIds)
            .param("f", family)
            .query(
                (rs, i) ->
                    new Lesson(rs.getLong(1), Rows.longOrNull(rs, "subject_id"), rs.getString(3)))
            .list()) {
      if (fits(l.subjectId(), l.title(), subjectId, mine, base)) {
        out.add(l.id());
      }
    }
    return out;
  }

  /**
   * Пара подходит подгруппе {@code mine}: своя; или общая (без номера в названии либо с её номером)
   * – у другой подгруппы того же предмета или без предмета, но с тем же названием.
   */
  static boolean fits(Long lessonSubject, String title, long subjectId, int mine, String baseKey) {
    if (lessonSubject != null && lessonSubject == subjectId) {
      return true;
    }
    int k = SubjectService.subgroupOf(title);
    if (k != 0 && k != mine) {
      return false;
    }
    if (lessonSubject != null) {
      return true; // подгруппа того же предмета (выборка – только по ним)
    }
    String t = key(title);
    return !t.isEmpty() && (t.equals(baseKey) || t.startsWith(baseKey + " "));
  }

  /** Название до номера подгруппы: «Иностранный язык №2 Начинающая» → «Иностранный язык». */
  static String base(String name) {
    return name.replaceAll("\\s*(№\\s*\\d+|\\(\\s*\\d+\\s*\\)).*$", "").strip();
  }

  /** Для сравнения: строчные, «ё» → «е», без номера подгруппы и знаков. */
  static String key(String s) {
    return base(s == null ? "" : s)
        .toLowerCase(Locale.ROOT)
        .replace('ё', 'е')
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .strip();
  }
}
