package hack.web.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Chooses where player records are persisted.
 *
 * <h2>Configuration</h2>
 * <pre>
 * ruhrohgue.scores.store      file (default) | s3
 * ruhrohgue.scores.dir        base directory the file store may write inside
 * ruhrohgue.scores.file       file name within that directory
 * ruhrohgue.scores.s3.bucket  bucket name, required when store=s3
 * ruhrohgue.scores.s3.key     object key, default ruhrohgue/scores.json
 * ruhrohgue.scores.s3.region  AWS region, default from the provider chain
 * </pre>
 *
 * <p>The file store is split into a base directory plus a file name on
 * purpose. Previously a single fully-qualified path was accepted, which meant
 * a value arriving from a deployment template could point anywhere on the
 * filesystem. The name is now resolved <em>within</em> the declared directory
 * and checked, so a {@code ../} cannot walk out of it.</p>
 *
 * <p>For Elastic Beanstalk, set {@code ruhrohgue.scores.dir} to a mounted EFS
 * path such as {@code /mnt/efs/ruhrohgue}, or switch to {@code store=s3}.
 * Leaving it at the default writes to the container's temporary directory,
 * which is wiped on every deployment — fine locally, wrong in production.</p>
 */
@Configuration
public class ScoreStoreConfig {

    @Value("${ruhrohgue.scores.store:file}")
    private String storeType;

    @Value("${ruhrohgue.scores.dir:#{systemProperties['java.io.tmpdir']}}")
    private String scoresDir;

    @Value("${ruhrohgue.scores.file:ruhrohgue-scores.json}")
    private String scoresFile;

    @Value("${ruhrohgue.scores.s3.bucket:}")
    private String s3Bucket;

    @Value("${ruhrohgue.scores.s3.key:ruhrohgue/scores.json}")
    private String s3Key;

    @Value("${ruhrohgue.scores.s3.region:}")
    private String s3Region;

    /**
     * Builds the configured store.
     *
     * <p>An S3 configuration that is missing its bucket falls back to the file
     * store with a loud warning rather than refusing to start. A game that
     * boots with a warning is better than an environment that will not come up
     * at all because of one unset variable — but the warning must be
     * impossible to miss in the logs.</p>
     */
    @Bean
    public ScoreStore scoreStore() {
        if ("s3".equalsIgnoreCase(storeType)) {
            if (s3Bucket == null || s3Bucket.isBlank()) {
                System.err.println("[ScoreStore] ruhrohgue.scores.store=s3 but "
                    + "ruhrohgue.scores.s3.bucket is not set. Falling back to the "
                    + "local file store — records will NOT survive a redeploy.");
            } else {
//                S3Client.Builder builder = S3Client.builder();
//                if (s3Region != null && !s3Region.isBlank()) {
//                    builder = builder.region(Region.of(s3Region));
//                }
//                S3ScoreStore store = new S3ScoreStore(builder.build(), s3Bucket, s3Key);
//                System.out.println("[ScoreStore] Player records: " + store.describe());
//                return store;
                // GEWIJZIGD: 'var' lost het type-probleem direct op zonder extra imports
                var builder = S3Client.builder();
                if (s3Region != null && !s3Region.isBlank()) {
                    builder = builder.region(Region.of(s3Region));
                }
                S3ScoreStore store = new S3ScoreStore(builder.build(), s3Bucket, s3Key);
                System.out.println("[ScoreStore] Player records: " + store.describe());
                return store;

            }
        }

        Path base = Paths.get(scoresDir);
        // Reject a file name carrying path syntax, then confine the result to
        // the declared base directory.
        String plainName = SafePaths.requirePlainFileName(scoresFile);
        Path resolved = SafePaths.resolveWithin(base, plainName);

        FileScoreStore store = new FileScoreStore(resolved);
        System.out.println("[ScoreStore] Player records: " + store.describe());
        if (isEphemeral(resolved)) {
            System.out.println("[ScoreStore] WARNING: this path looks like container "
                + "temporary storage. On AWS Elastic Beanstalk it is wiped on every "
                + "deployment and is not shared between instances. Set "
                + "ruhrohgue.scores.dir to an EFS mount, or ruhrohgue.scores.store=s3.");
        }
        return store;
    }

    /** True when the path is under the JVM temporary directory. */
    private static boolean isEphemeral(Path p) {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null) return false;
        return p.startsWith(Paths.get(tmp).toAbsolutePath().normalize());
    }
}
