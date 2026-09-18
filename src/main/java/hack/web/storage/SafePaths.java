package hack.web.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Confines file access to a declared base directory.
 *
 * <h2>Why this exists</h2>
 * <p>The score-file location is configurable. Anything configurable is
 * eventually set from somewhere less trusted than an operator's command line —
 * an environment variable populated from a deployment template, a value read
 * out of a configuration service, or a future endpoint that accepts a profile
 * name. The moment a path is assembled from an outside value, {@code ../}
 * segments let it escape upward into application logs, container secrets or
 * {@code /etc}.</p>
 *
 * <p>These helpers resolve a candidate path and then verify the
 * <em>normalised, absolute</em> result is still inside the permitted base
 * directory. Normalising first matters: {@code /data/../etc/passwd} only
 * reveals itself as an escape once the {@code ..} has been collapsed.</p>
 */
public final class SafePaths {

    private SafePaths() { }

    /**
     * Resolves {@code candidate} and confirms it stays within {@code base}.
     *
     * @param base      the directory the result must remain inside
     * @param candidate a path, absolute or relative to {@code base}
     * @return the normalised absolute path
     * @throws SecurityException when the candidate escapes the base directory
     */
    public static Path resolveWithin(Path base, String candidate) {
        if (base == null) throw new IllegalArgumentException("base must not be null");
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("candidate must not be blank");
        }

        Path normalisedBase = base.toAbsolutePath().normalize();
        Path resolved = Paths.get(candidate);
        if (!resolved.isAbsolute()) {
            resolved = normalisedBase.resolve(resolved);
        }
        resolved = resolved.toAbsolutePath().normalize();

        if (!resolved.startsWith(normalisedBase)) {
            throw new SecurityException(
                "Refusing path outside the permitted directory: " + candidate
                + " resolves to " + resolved + ", which is not under " + normalisedBase);
        }
        return resolved;
    }

    /**
     * Rejects any single filename component that could alter the directory.
     *
     * <p>Used for values that must be a plain file name and nothing else —
     * no separators, no parent references, no absolute roots.</p>
     *
     * @param fileName candidate file name
     * @return the same name when it is safe
     * @throws SecurityException when the name contains path syntax
     */
    public static String requirePlainFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
        if (fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..") || fileName.contains("\0")) {
            throw new SecurityException("Unsafe file name: " + fileName);
        }
        return fileName;
    }

    /** Creates the parent directory of {@code file} when it does not exist. */
    public static void ensureParentDirectory(Path file) throws IOException {
        Path parent = file.toAbsolutePath().normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
