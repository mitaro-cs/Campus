package app.groupbase.web.api;

import app.groupbase.auth.Actor;
import app.groupbase.auth.Permission;
import app.groupbase.content.Activity;
import app.groupbase.web.Require;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Рейтинг активности группы (1.0.4): «Участники → Рейтинг». */
@RestController
class ActivityController {

  private final Activity activity;

  ActivityController(Activity activity) {
    this.activity = activity;
  }

  @Require(Permission.VIEW_GROUP)
  @GetMapping("/api/groups/{groupId}/activity")
  Activity.Board board(
      Actor actor,
      @PathVariable long groupId,
      @RequestParam(defaultValue = "month") String period) {
    return activity.board(actor.id(), groupId, period);
  }
}
