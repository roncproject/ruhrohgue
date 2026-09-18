package hack.web.storage;

import java.io.IOException;
import java.util.List;

/**
 * Where the player-record table is persisted.
 *
 * <h2>Why this is an interface</h2>
 * <p>Elastic Beanstalk container instances are ephemeral. Anything written to
 * the container filesystem is destroyed when the environment scales in, scales
 * out, or is updated — so a score file written to {@code /tmp} inside the image
 * is lost on the next deployment, and worse, two instances behind the load
 * balancer keep two divergent tables that overwrite each other's view of the
 * truth.</p>
 *
 * <p>Separating the storage decision from the score logic makes the durable
 * options configuration rather than a rewrite:</p>
 *
 * <ul>
 *   <li>{@link FileScoreStore} pointed at a local path — development only.</li>
 *   <li>{@link FileScoreStore} pointed at a mounted <b>Amazon EFS</b> volume —
 *       durable and shared across instances, with no code change.</li>
 *   <li>{@link S3ScoreStore} — durable object storage, no volume to mount.</li>
 * </ul>
 *
 * <p>Implementations are used behind a lock held by the score service, so they
 * do not need to be internally synchronised. They must, however, make each
 * individual write <em>atomic</em>: a torn write leaves the table unreadable
 * for every later start-up.</p>
 */
public interface ScoreStore {

    /**
     * Reads every stored record line.
     *
     * @return the lines, oldest ordering irrelevant; empty when nothing stored
     * @throws IOException when the underlying store cannot be read
     */
    List<String> readLines() throws IOException;

    /**
     * Replaces the stored records atomically.
     *
     * <p>Atomic means a concurrent or interrupted reader sees either the old
     * content or the new content, never a half-written file.</p>
     *
     * @param lines the complete new contents
     * @throws IOException when the underlying store cannot be written
     */
    void writeLines(List<String> lines) throws IOException;

    /**
     * Removes all stored records.
     *
     * @throws IOException when the underlying store cannot be modified
     */
    void deleteAll() throws IOException;

    /**
     * A human-readable description of where records are stored, for the
     * start-up banner and the operator-facing endpoint.
     *
     * @return e.g. {@code file:/mnt/efs/ruhrohgue/scores.json}
     */
    String describe();
}
