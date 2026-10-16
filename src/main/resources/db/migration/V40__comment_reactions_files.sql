-- Комментарии (1.0.4): реакции – как у новостей, и с несогласием (👎); картинки и гифки.
CREATE TABLE comment_reactions (
  comment_id INTEGER NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
  user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  emoji      TEXT    NOT NULL,
  at         INTEGER NOT NULL,
  PRIMARY KEY (comment_id, user_id, emoji)
) STRICT;

CREATE INDEX comment_reactions_user ON comment_reactions (user_id);

CREATE TABLE comment_attachments (
  comment_id INTEGER NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
  file_id    INTEGER NOT NULL REFERENCES files (id) ON DELETE CASCADE,
  position   INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (comment_id, file_id)
) STRICT, WITHOUT ROWID;

CREATE INDEX comment_attachments_file ON comment_attachments (file_id);
