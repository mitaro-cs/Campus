package app.groupbase.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubgroupLessonsTest {

  @Test
  void commonLessonByNameWhenItHasNoSubject() {
    String base = SubgroupLessons.key("Иностранный язык №2 Начинающая группа");
    assertThat(base).isEqualTo("иностранный язык");
    assertThat(SubgroupLessons.fits(null, "Иностранный язык", 7, 2, base)).isTrue();
    assertThat(SubgroupLessons.fits(null, "Иностранный язык (2 подгр.)", 7, 2, base)).isTrue();
    assertThat(SubgroupLessons.fits(null, "Иностранный язык (1 подгр.)", 7, 2, base)).isFalse();
    assertThat(SubgroupLessons.fits(null, "Физика", 7, 2, base)).isFalse();
    // Своя – всегда; пара «№1» без номера в названии – общая.
    assertThat(SubgroupLessons.fits(7L, "что угодно", 7, 2, base)).isTrue();
    assertThat(SubgroupLessons.fits(5L, "Иностранный язык", 7, 2, base)).isTrue();
  }
}
