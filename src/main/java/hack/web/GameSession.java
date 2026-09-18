package hack.web;

import hack.model.GameState;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * GameSession.java — HTTP-session-scoped holder for a single {@link GameState}.
 *
 * <p>Annotated with {@code @SessionScope} so Spring creates one instance per
 * browser HTTP session (cookie {@code JSESSIONID}).  Multiple browser tabs on
 * the same browser share a session; different browsers get independent games.</p>
 *
 * <p>This replaces the single global {@code GameState} field in the original
 * {@code HackServer}, giving every player their own independent game on the
 * same server instance.</p>
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION,
       proxyMode = ScopedProxyMode.TARGET_CLASS)
public class GameSession {

    /** The player's current game state, or {@code null} before the first /new call. */
    private GameState state;

    /** Returns the current game state, or {@code null} if no game has been started. */
    public GameState getState() {
        return state;
    }

    /** Replaces the current game state (called by POST /new). */
    public void setState(GameState state) {
        this.state = state;
    }
}
