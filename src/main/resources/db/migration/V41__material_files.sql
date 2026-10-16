-- Тема в материалах (1.0.4): несколько файлов одним материалом, листаются в просмотре. Первый –
-- по-прежнему materials.file_id (обложка, поиск, копия на устройстве), остальные – здесь.
CREATE TABLE material_files (
  material_id INTEGER NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
  file_id     INTEGER NOT NULL REFERENCES files (id) ON DELETE CASCADE,
  position    INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (material_id, file_id)
) STRICT, WITHOUT ROWID;

CREATE INDEX material_files_file ON material_files (file_id);
