package hack.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Validated request for starting a new game.
 *
 * <h2>Why this exists</h2>
 * <p>The name field is three characters long, which reads as harmless. It is
 * not. The value flows into the score file, into the JSON written there, and
 * back out into the browser on the score screen. Before this class existed the
 * field accepted <em>any</em> printable ASCII, which left three distinct
 * vectors open:</p>
 *
 * <ul>
 *   <li><b>Stored cross-site scripting.</b> A name such as {@code <b>} or
 *       {@code "><} is persisted and later rendered on the high-score board
 *       for every visitor.</li>
 *   <li><b>JSON syntax splitting.</b> The score file is written as JSON Lines
 *       by string concatenation. A name containing {@code "} or {@code \}
 *       could terminate the string early and inject arbitrary fields into the
 *       record, or corrupt the line so that every later read fails.</li>
 *   <li><b>Path traversal.</b> Any code path that derives a filename from a
 *       player-supplied value can be walked upward with {@code ..} and
 *       {@code /} segments.</li>
 * </ul>
 *
 * <p>Restricting the field to exactly three alphanumeric characters closes all
 * three at the controller boundary, before the value reaches any storage or
 * rendering layer. This is defence in depth rather than the only defence:
 * {@code HighScoreEntry.toJson()} still escapes what it writes, and
 * {@code SafePaths} still confines file access. But validating first means the
 * dangerous value never enters the system at all.</p>
 *
 * @param playerName three alphanumeric characters, e.g. {@code T01}
 * @param role       character class; validated against a fixed set elsewhere
 * @param seed       optional dungeon seed; parsed leniently
 */
public class NewGameRequest {

    /** The regular expression every player name must match. */
    public static final String NAME_PATTERN = "^[a-zA-Z0-9]{3}$";

    @NotBlank(message = "Name cannot be empty")
    @Size(min = 3, max = 3, message = "Name must be exactly 3 characters")
    @Pattern(regexp = NAME_PATTERN,
             message = "Name must be exactly 3 letters or digits (A-Z, a-z, 0-9)")
    private String playerName;

    private String role;
    private String seed;

    public NewGameRequest() { }

    public NewGameRequest(String playerName, String role, String seed) {
        this.playerName = playerName;
        this.role = role;
        this.seed = seed;
    }

    public String getPlayerName()            { return playerName; }
    public void   setPlayerName(String name) { this.playerName = name; }

    public String getRole()              { return role; }
    public void   setRole(String role)   { this.role = role; }

    public String getSeed()              { return seed; }
    public void   setSeed(String seed)   { this.seed = seed; }

    /**
     * Static check usable outside the Bean Validation lifecycle.
     *
     * @param name candidate player name
     * @return true when the name is exactly three alphanumeric characters
     */
    public static boolean isValidName(String name) {
        return name != null && name.matches(NAME_PATTERN);
    }
}
