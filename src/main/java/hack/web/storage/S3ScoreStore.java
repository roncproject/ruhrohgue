package hack.web.storage;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores player records as a single object in an Amazon S3 bucket.
 *
 * <p>The alternative to a mounted EFS volume. S3 needs no volume, no mount
 * target and no VPC plumbing, and it survives instance replacement by
 * construction — which is the whole point on an ephemeral Elastic Beanstalk
 * container.</p>
 *
 * <h2>Concurrency caveat, stated plainly</h2>
 * <p>An S3 {@code PutObject} is atomic for a single writer: a reader sees
 * either the old object or the new one, never a partial upload. It is
 * <em>not</em> atomic across writers. Two application instances that finish
 * games at the same moment will both read, both append, and the second write
 * wins — losing the first instance's entry.</p>
 *
 * <p>This is acceptable for a high-score table where the cost of a rare lost
 * entry is low, and it is documented rather than hidden. If last-write-wins
 * ever becomes unacceptable, the fix is a conditional write: S3 supports
 * {@code If-Match} on an ETag, so the service can retry when the object has
 * changed underneath it. That is deliberately not built here — it adds a retry
 * loop for a problem this application does not yet have.</p>
 *
 * <p>Enabled by setting {@code ruhrohgue.scores.store=s3} together with
 * {@code ruhrohgue.scores.s3.bucket}. Credentials come from the default AWS
 * provider chain, which on Elastic Beanstalk means the EC2 instance profile —
 * never a key baked into the image.</p>
 */
public class S3ScoreStore implements ScoreStore {

    private final S3Client client;
    private final String bucket;
    private final String key;

    /**
     * @param client configured S3 client
     * @param bucket destination bucket name
     * @param key    object key, e.g. {@code ruhrohgue/scores.json}
     */
    public S3ScoreStore(S3Client client, String bucket, String key) {
        this.client = client;
        this.bucket = bucket;
        // An object key is not a filesystem path, but a key assembled from an
        // outside value can still be steered somewhere unintended. Reject the
        // traversal syntax rather than trusting the caller.
        if (key.contains("..")) {
            throw new SecurityException("Unsafe S3 key: " + key);
        }
        this.key = key;
    }

    @Override
    public List<String> readLines() throws IOException {
        try {
            ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
            String body = bytes.asString(StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            for (String line : body.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            return out;
        } catch (NoSuchKeyException e) {
            return new ArrayList<>();          // no table yet is not an error
        } catch (S3Exception e) {
            throw new IOException("Could not read scores from s3://"
                + bucket + "/" + key, e);
        }
    }

    @Override
    public void writeLines(List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append('\n');
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/x-ndjson")
                    .build(),
                RequestBody.fromString(sb.toString(), StandardCharsets.UTF_8));
        } catch (S3Exception e) {
            throw new IOException("Could not write scores to s3://"
                + bucket + "/" + key, e);
        }
    }

    @Override
    public void deleteAll() throws IOException {
        try {
            client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            // Already absent — the desired end state.
        } catch (S3Exception e) {
            throw new IOException("Could not delete s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public String describe() {
        return "s3://" + bucket + "/" + key;
    }
}
