package app.groupbase.store;

import app.groupbase.auth.GroupRole;
import app.groupbase.auth.InstanceRole;

/** Участник группы вместе с его аккаунтом. */
public record Member(
    long userId,
    String username,
    String displayName,
    String avatar,
    User.Status status,
    GroupRole role,
    InstanceRole instanceRole,
    long joinedAt,
    // Статус (1.0.4): строка «для себя» под именем; null – нет.
    String mood) {

  /** Копия без логина — для тех, кому его видеть не положено. */
  public Member withoutUsername() {
    return new Member(
        userId, null, displayName, avatar, status, role, instanceRole, joinedAt, mood);
  }
}
