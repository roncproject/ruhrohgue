package hack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HackApplication.java — Spring Boot entry point for Hack 1.0.2 AWS Web Edition.
 *
 * <p>Replaces the original {@code HackMain} + {@code HackServer} (which used
 * {@code com.sun.net.httpserver}) with a Spring Boot / embedded-Tomcat stack
 * so the application can be deployed as a single executable JAR on AWS
 * Elastic Beanstalk, App Runner, or ECS.</p>
 *
 * <p>Port resolution order (highest priority first):
 * <ol>
 *   <li>AWS Elastic Beanstalk injects {@code PORT} as an env-var → picked up by
 *       Spring via {@code server.port=${PORT:5000}} in application.properties.</li>
 *   <li>Procfile overrides: {@code web: java -jar ruhrohgue.jar --server.port=5000}</li>
 *   <li>Default: 5000 (matching the EB Docker-platform convention used by rogue-aws).</li>
 * </ol>
 * </p>
 */
@SpringBootApplication
public class HackApplication {

    /**
     * Optional --seed flag makes dungeon generation reproducible:
     *   java -jar ruhrohgue.jar --seed 12345
     * Without --seed, a random seed based on current time is used.
     */
    /**
     * The configured seed value.  When --seed N is given on the command line,
     * this is set to N and every new game re-seeds Dice with this value.
     * When no --seed is given, this stays -1 and each new game uses a fresh
     * System.currentTimeMillis() seed.
     */
    public static long CONFIGURED_SEED = -1L;

    /**
     * Parses the optional {@code --seed N} command-line flag.
     *
     * <p>Extracted from {@link #main(String[])} so it can be tested without
     * starting Spring. It previously lived inline in {@code main}, which meant
     * the only way to exercise it was to boot an embedded Tomcat — the reason
     * the original application test was slow and brittle.</p>
     *
     * <p>A malformed value is reported and treated as "no seed given" rather
     * than aborting start-up: refusing to launch the whole server over one
     * mistyped digit would be a worse failure than playing a random dungeon.</p>
     *
     * @param args raw command-line arguments; may be {@code null}
     * @return the parsed seed, or {@code -1} when none was supplied
     */
    public static long parseSeedArg(String[] args) {
        if (args == null) return -1L;
        long seed = -1L;
        for (int i = 0; i < args.length - 1; i++) {
            if (!"--seed".equalsIgnoreCase(args[i])) continue;
            try {
                seed = Long.parseLong(args[i + 1].trim());
            } catch (NumberFormatException e) {
                System.err.println("[RuhRohgue] Invalid seed value: " + args[i + 1]);
            }
        }
        return seed;
    }

    public static void main(String[] args) {
        long seed = parseSeedArg(args);
        if (seed >= 0) {
            System.out.println("[RuhRohgue] Using fixed seed: " + seed);
            System.out.println("[RuhRohgue] Every new game will produce"
                + " the same dungeon layout.");
        }
        CONFIGURED_SEED = seed;
        // Initial seed — replaced per-game in HackController.newGame().
        hack.model.Dice.seed(seed >= 0 ? seed : System.currentTimeMillis());
        // The storage location is resolved and logged by ScoreStoreConfig,
        // which also warns when it points at ephemeral container storage.
        SpringApplication.run(HackApplication.class, args);
    }
}
