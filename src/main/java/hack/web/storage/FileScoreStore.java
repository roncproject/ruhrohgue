package hack.web.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores player records in a JSON Lines file.
 *
 * <p>Suitable for local development, and for production when the configured
 * path is a mounted <b>Amazon EFS</b> volume — EFS survives instance
 * replacement and is shared by every instance behind the load balancer, which
 * is what makes it a legitimate production choice rather than a development
 * shortcut.</p>
 *
 * <h2>Atomic writes</h2>
 * <p>The previous implementation opened the real file with a truncating writer
 * and wrote records into it one at a time. A crash, a container stop, or a
 * second writer arriving mid-loop left a truncated file, and every subsequent
 * start-up read a corrupt table. Writes now go to a temporary file in the same
 * directory and are moved into place with {@link StandardCopyOption#ATOMIC_MOVE},
 * so a reader sees either the complete previous table or the complete new one.
 * The move must be same-directory: an atomic move across filesystems is not
 * supported and would silently fall back to a copy.</p>
 */
public class FileScoreStore implements ScoreStore {

    private final Path file;

    /**
     * @param file destination file; its parent directory is created if absent
     */
    public FileScoreStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    /** The resolved absolute path in use. */
    public Path path() { return file; }

    @Override
    public List<String> readLines() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    @Override
    public void writeLines(List<String> lines) throws IOException {
        SafePaths.ensureParentDirectory(file);

        // Temp file in the SAME directory, so the move below can be atomic.
        Path dir = file.getParent();
        Path tmp = Files.createTempFile(dir, ".scores-", ".tmp");
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) sb.append(line).append(System.lineSeparator());
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);

            try {
                Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some network filesystems refuse atomic moves. A plain replace
                // is a narrower window than writing in place, so take it and
                // say so rather than failing the save outright.
                System.err.println("[ScoreStore] Atomic move unavailable on this "
                    + "filesystem; falling back to replace. Path: " + file);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public void deleteAll() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    public String describe() {
        return "file:" + file;
    }
}
