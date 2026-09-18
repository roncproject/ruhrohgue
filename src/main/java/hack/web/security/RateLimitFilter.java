package hack.web.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-client request rate limiting.
 *
 * <h2>Why</h2>
 * <p>Every keypress is a {@code POST /command}, and every completed game writes
 * the score table. Without a limit a single client can drive the request loop
 * and the storage layer as fast as the network allows, exhausting the servlet
 * thread pool and hammering the score store. That is a denial of service that
 * needs no vulnerability at all — only a loop.</p>
 *
 * <h2>Why not Bucket4j</h2>
 * <p>Bucket4j is the usual recommendation and it is a good library. For one
 * in-process token bucket it is also an extra dependency, an extra transitive
 * tree and an extra thing to keep patched. The algorithm below is roughly
 * thirty lines and has no dependencies. If this application ever needs
 * distributed limiting shared across Elastic Beanstalk instances, Bucket4j
 * backed by ElastiCache becomes the right answer — an in-process bucket cannot
 * do that, and pretending otherwise would be the real mistake.</p>
 *
 * <h2>Algorithm</h2>
 * <p>A token bucket per client key. Tokens refill continuously at
 * {@code capacity / refillPeriod}; a request costs one token. A client with an
 * empty bucket receives {@code 429 Too Many Requests} with a {@code Retry-After}
 * header. Buckets are evicted once the map grows past a bound, so the map
 * itself cannot become the memory-exhaustion vector it is meant to prevent.</p>
 */
@Component
public class RateLimitFilter implements Filter {

    /** Requests allowed per refill window, per client. */
    @Value("${ruhrohgue.ratelimit.capacity:120}")
    private int capacity;

    /** Refill window in seconds. */
    @Value("${ruhrohgue.ratelimit.window-seconds:60}")
    private long windowSeconds;

    /** Master switch, so a load test can turn it off deliberately. */
    @Value("${ruhrohgue.ratelimit.enabled:true}")
    private boolean enabled;

    /** Upper bound on tracked clients, to cap memory. */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled || !(request instanceof HttpServletRequest req)) {
            chain.doFilter(request, response);
            return;
        }

        String path = req.getRequestURI();
        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(req);
        Bucket bucket = bucketFor(key);

        if (bucket.tryConsume(capacity, windowSeconds)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse res = (HttpServletResponse) response;
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(Math.max(1, windowSeconds / 4)));
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
            "{\"error\":\"rate_limited\",\"message\":\"Too many requests. Slow down.\"}");
    }

    /**
     * Paths that never consume a token.
     *
     * <p>The health check must never be throttled: the load balancer would
     * conclude a working instance is unhealthy and replace it.</p>
     *
     * <p>Static assets are exempt for a different reason. They are fetched in a
     * burst on first load and then served from the browser and service-worker
     * caches, so counting them against the same budget as gameplay meant a
     * page refresh could spend a large share of a player's allowance before
     * they had pressed a single key. Throttling them protects nothing: they
     * are immutable files the CDN and the browser cache anyway.</p>
     */
    // Package-private so RateLimitFilterTest can verify the exemption list
    // without standing up a servlet container.
    static boolean isExempt(String path) {
        if (path == null) return false;
        return path.startsWith("/actuator/health")
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/icons/")
            || path.equals("/manifest.json")
            || path.equals("/sw.js")
            || path.equals("/favicon.ico")
            || path.equals("/robots.txt")
            || path.equals("/sitemap.xml");
    }

    private Bucket bucketFor(String key) {
        // Evict wholesale rather than tracking an LRU: this map exists to stop
        // abuse, not to be precise, and an unbounded map is its own DoS.
        if (buckets.size() > MAX_TRACKED_CLIENTS) buckets.clear();
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity));
    }

    /**
     * Identifies the client securely.
     *
     * <p>Behind the Cloudflare proxy, the leftmost 'CF-Connecting-IP' header
     * carries the authentic user address. If absent (such as during local development),
     * it falls back to 'X-Forwarded-For' or the remote socket connection address.</p>
     */
    private static String clientKey(HttpServletRequest req) {
        // 1. Check Cloudflare header first (Active when live on the internet behind Cloudflare)
        String cfIp = req.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }

        // 2. Fallback to standard proxy header (Active behind standard AWS load balancers / Nginx)
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty() && first.length() <= 45) return first;   // max IPv6 length
        }
        
        // 3. Fallback to local network socket addr (Active during local PC testing)
        String remote = req.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /** A continuously refilling token bucket. */
    private static final class Bucket {
        private final AtomicLong tokensMilli;   // tokens x1000, for fractional refill
        private final AtomicLong lastRefillNanos;

        Bucket(int capacity) {
            this.tokensMilli = new AtomicLong(capacity * 1000L);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        /**
         * Refills according to elapsed time, then takes one token.
         *
         * @return true when a token was available
         */
        synchronized boolean tryConsume(int capacity, long windowSeconds) {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos.getAndSet(now);
            if (elapsed > 0 && windowSeconds > 0) {
                double windowNanos = windowSeconds * 1_000_000_000.0;
                long refill = (long) ((elapsed / windowNanos) * capacity * 1000L);
                if (refill > 0) {
                    long updated = Math.min(capacity * 1000L, tokensMilli.get() + refill);
                    tokensMilli.set(updated);
                }
            }
            if (tokensMilli.get() >= 1000L) {
                tokensMilli.addAndGet(-1000L);
                return true;
            }
            return false;
        }
    }
}
