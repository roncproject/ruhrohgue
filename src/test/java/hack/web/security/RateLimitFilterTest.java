package hack.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rate limiter's exemption list.
 *
 * Background: every keypress is a POST /command, so the limiter sits directly
 * in the game's input path. Two things must never consume a token — the load
 * balancer's health check, and the static assets a page load fetches in a
 * burst — or a player spends their allowance before pressing a key, and the
 * resulting 429s feel exactly like input lag.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("RateLimitFilter exemptions")
class RateLimitFilterTest {

    @Test
    @DisplayName("isExempt_healthCheck_isExempt")
    void isExempt_healthCheck_isExempt() {
        assertTrue(RateLimitFilter.isExempt("/actuator/health"),
            "Throttling the health check would make the load balancer replace "
            + "a perfectly healthy instance");
    }

    @Test
    @DisplayName("isExempt_staticAssets_areExempt")
    void isExempt_staticAssets_areExempt() {
        assertTrue(RateLimitFilter.isExempt("/css/hack.css"));
        assertTrue(RateLimitFilter.isExempt("/js/hack.js"));
        assertTrue(RateLimitFilter.isExempt("/icons/icon-192.png"));
        assertTrue(RateLimitFilter.isExempt("/manifest.json"));
        assertTrue(RateLimitFilter.isExempt("/sw.js"));
    }

    @Test
    @DisplayName("isExempt_crawlerFiles_areExempt")
    void isExempt_crawlerFiles_areExempt() {
        assertTrue(RateLimitFilter.isExempt("/robots.txt"));
        assertTrue(RateLimitFilter.isExempt("/sitemap.xml"));
    }

    @Test
    @DisplayName("isExempt_gameEndpoints_areNotExempt")
    void isExempt_gameEndpoints_areNotExempt() {
        assertFalse(RateLimitFilter.isExempt("/command"),
            "Gameplay must remain rate limited — it is the flood vector");
        assertFalse(RateLimitFilter.isExempt("/new"));
        assertFalse(RateLimitFilter.isExempt("/state"));
        assertFalse(RateLimitFilter.isExempt("/"));
    }

    @Test
    @DisplayName("isExempt_otherActuatorEndpoints_areNotExempt")
    void isExempt_otherActuatorEndpoints_areNotExempt() {
        assertFalse(RateLimitFilter.isExempt("/actuator/env"),
            "Only the health check is exempt, not the whole actuator surface");
    }

    @Test
    @DisplayName("isExempt_nullPath_isNotExempt")
    void isExempt_nullPath_isNotExempt() {
        assertFalse(RateLimitFilter.isExempt(null));
    }
}
