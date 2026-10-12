-- Правка комментариев (1.0.4): свой – автор, любой – модератор; когда правили – для пометки «изменено».
ALTER TABLE comments ADD COLUMN edited_at INTEGER;
