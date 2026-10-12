package app.groupbase.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** Текст файлов для поиска (1.0.2). */
class FileTextTest {

  private static byte[] zip(String... nameAndXml) throws Exception {
    var out = new ByteArrayOutputStream();
    try (var z = new ZipOutputStream(out)) {
      for (int i = 0; i < nameAndXml.length; i += 2) {
        z.putNextEntry(new ZipEntry(nameAndXml[i]));
        z.write(nameAndXml[i + 1].getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
      }
    }
    return out.toByteArray();
  }

  @Test
  void knowsWhichFilesHaveText() {
    assertThat(FileText.kind("Лекция.PDF", "application/octet-stream")).isEqualTo("pdf");
    assertThat(FileText.kind("отчёт.docx", null)).isEqualTo("docx");
    assertThat(FileText.kind("конспект.md", null)).isEqualTo("text");
    assertThat(FileText.kind("data", "text/plain")).isEqualTo("text");
    assertThat(FileText.kind("фото.jpg", "image/jpeg")).isNull();
  }

  @Test
  void wordAndSlidesBecomeText() throws Exception {
    String docx =
        FileText.extract(
            "a.docx",
            null,
            zip(
                "word/document.xml",
                "<w:p><w:r><w:t>Расчёт &amp; анализ</w:t></w:r></w:p><w:p><w:t>Вывод</w:t></w:p>"));
    assertThat(docx).isEqualTo("Расчет & анализ\nВывод");

    // Слайды – по номерам, «slide10» после «slide2».
    String pptx =
        FileText.extract(
            "b.pptx",
            null,
            zip(
                "ppt/slides/slide10.xml", "<a:p><a:t>десятый</a:t></a:p>",
                "ppt/slides/slide2.xml", "<a:p><a:t>второй</a:t></a:p>",
                "ppt/media/x.xml", "<a:t>не текст слайда</a:t>"));
    assertThat(pptx).isEqualTo("второй\nдесятый");

    assertThat(FileText.extract("c.md", null, "# Ёлка\n\n\nтекст".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("# Елка\nтекст");
    assertThat(FileText.extract("d.jpg", "image/jpeg", new byte[] {1, 2})).isEmpty();
  }

  @Test
  void manyHugePartsStopAtTheTotalLimit() throws Exception {
    // Презентация из множества больших слайдов: читается не больше MAX_TOTAL, без падения.
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] slide = ("<a:p>" + "слово ".repeat(1000) + "</a:p>").getBytes(StandardCharsets.UTF_8);
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(out)) {
      z.setLevel(9);
      for (int i = 1; i <= 3000; i++) {
        z.putNextEntry(new java.util.zip.ZipEntry("ppt/slides/slide" + i + ".xml"));
        z.write(slide);
        z.closeEntry();
      }
    }
    String text = FileText.extract("big.pptx", null, out.toByteArray());
    assertThat(text).startsWith("слово");
    assertThat(text.length()).isLessThanOrEqualTo(FileText.MAX_CHARS);
  }
}
