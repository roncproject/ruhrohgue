package hack.web;

import hack.model.Dice;
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

    /**
     * This session's own RNG state (TRK-02A / TRK-02B) — one {@link Dice.State}
     * per browser session, bound to {@link Dice} by the controller at the top
     * of every request that touches game logic. Living here rather than as
     * static fields on Dice is what stops one session's /new or level
     * generation from disturbing any other session's game.
     */
    private final Dice.State diceState = new Dice.State();

    /** Returns the current game state, or {@code null} if no game has been started. */
    public GameState getState() {
        return state;
    }

    /** Replaces the current game state (called by POST /new). */
    public void setState(GameState state) {
        this.state = state;
    }

    /** Returns this session's own Dice RNG state. */
    public Dice.State getDiceState() {
        return diceState;
    }
}
