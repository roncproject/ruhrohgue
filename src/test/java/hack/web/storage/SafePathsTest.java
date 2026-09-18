package hack.web.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the path-traversal guard.
 *
 * Normalising before comparing is the part that matters: "/base/../etc/passwd"
 * only reveals itself as an escape once the ".." has been collapsed.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("SafePaths")
class SafePathsTest {

    @Test
    @DisplayName("resolveWithin_plainFileName_resolvesInsideBase")
    void resolveWithin_plainFileName_resolvesInsideBase(@TempDir Path base) {
        Path resolved = SafePaths.resolveWithin(base, "scores.json");
        assertTrue(resolved.startsWith(base.toAbsolutePath().normalize()));
        assertEquals("scores.json", resolved.getFileName().toString());
    }

    @Test
    @DisplayName("resolveWithin_parentTraversal_throwsSecurityException")
    void resolveWithin_parentTraversal_throwsSecurityException(@TempDir Path base) {
        assertThrows(SecurityException.class,
            () -> SafePaths.resolveWithin(base, "../escaped.json"));
        assertThrows(SecurityException.class,
            () -> SafePaths.resolveWithin(base, "../../../../etc/passwd"));
    }

    @Test
    @DisplayName("resolveWithin_traversalHiddenMidPath_throwsSecurityException")
    void resolveWithin_traversalHiddenMidPath_throwsSecurityException(@TempDir Path base) {
        // Only visible once normalised — the case a naive startsWith misses
        assertThrows(SecurityException.class,
            () -> SafePaths.resolveWithin(base, "sub/../../outside.json"));
    }

    @Test
    @DisplayName("resolveWithin_absolutePathOutsideBase_throwsSecurityException")
    void resolveWithin_absolutePathOutsideBase_throwsSecurityException(@TempDir Path base) {
        assertThrows(SecurityException.class,
            () -> SafePaths.resolveWithin(base, "/etc/passwd"));
    }

    @Test
    @DisplayName("resolveWithin_nestedPathInsideBase_isAllowed")
    void resolveWithin_nestedPathInsideBase_isAllowed(@TempDir Path base) {
        Path resolved = SafePaths.resolveWithin(base, "sub/dir/scores.json");
        assertTrue(resolved.startsWith(base.toAbsolutePath().normalize()));
    }

    @Test
    @DisplayName("requirePlainFileName_pathSyntax_throwsSecurityException")
    void requirePlainFileName_pathSyntax_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> SafePaths.requirePlainFileName("../x"));
        assertThrows(SecurityException.class, () -> SafePaths.requirePlainFileName("a/b"));
        assertThrows(SecurityException.class, () -> SafePaths.requirePlainFileName("a\\b"));
    }

    @Test
    @DisplayName("requirePlainFileName_ordinaryName_isReturned")
    void requirePlainFileName_ordinaryName_isReturned() {
        assertEquals("scores.json", SafePaths.requirePlainFileName("scores.json"));
    }
}
