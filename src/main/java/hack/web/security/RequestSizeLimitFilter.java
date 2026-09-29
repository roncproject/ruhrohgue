package hack.web.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Caps request body size for every request, closing a gap
 * {@code server.tomcat.max-http-form-post-size} does not cover.
 *
 * <h2>Why this exists (TRK-08)</h2>
 * <p>TRK-03 moved {@code POST /api/v1/new} and {@code POST /api/v1/command}
 * from form-urlencoded to JSON, read via {@code @RequestBody}. Tomcat's
 * {@code max-http-form-post-size} only bounds its own form-parameter
 * parsing — it never sees a raw body a message converter reads directly
 * off the input stream, so that setting stopped protecting these two
 * endpoints the moment they became JSON. The real payloads are tiny (a
 * 3-character name, a single command character), so a generous-but-small
 * ceiling costs nothing legitimate.</p>
 *
 * <p>Applied to every request rather than tracked by path, so an endpoint
 * added later inherits the protection automatically instead of relying on
 * someone remembering to extend an allow-list.</p>
 *
 * <h2>Two checks, not one</h2>
 * <p>{@code Content-Length} is checked first for a fast rejection before a
 * single byte is read. A client can lie about it, omit it, or stream a
 * chunked body with no length header at all — so the input stream is also
 * wrapped to hard-cap actual bytes read, regardless of what the header
 * claimed. The two paths fail differently on purpose: an oversized
 * declared length gets a clean {@code 413} straight from this filter;
 * exceeding the cap mid-stream surfaces as a read failure the message
 * converter turns into a generic {@code 400} further down the chain. Both
 * stop the request before an oversized body is fully buffered in memory,
 * which is the actual goal.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter implements Filter {

    @Value("${ruhrohgue.request.max-body-bytes:1024}")
    private int maxBodyBytes;

    public RequestSizeLimitFilter() { }

    // Package-private, for RequestSizeLimitFilterTest: field injection via
    // @Value happens after construction regardless of which constructor
    // ran, so production wiring (the no-arg constructor above, used by
    // Spring's component scan) is unaffected by this one existing.
    RequestSizeLimitFilter(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }

        if (req.getContentLengthLong() > maxBodyBytes) {
            res.setStatus(413);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(
                "{\"error\":\"payload_too_large\",\"message\":\"Request body exceeds the allowed size.\"}");
            res.getWriter().flush();
            return;
        }

        chain.doFilter(new SizeLimitedRequest(req, maxBodyBytes), response);
    }

    /** Wraps the input stream so a lying or absent Content-Length can't bypass the cap. */
    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {
        private final int limit;

        SizeLimitedRequest(HttpServletRequest request, int limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), limit);
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int limit;
        private int readSoFar = 0;

        LimitedServletInputStream(ServletInputStream delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (readSoFar >= limit) throw new IOException("Request body exceeds the allowed size.");
            int b = delegate.read();
            if (b != -1) readSoFar++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (readSoFar >= limit) throw new IOException("Request body exceeds the allowed size.");
            int allowed = Math.min(len, limit - readSoFar);
            int n = delegate.read(b, off, allowed);
            if (n > 0) readSoFar += n;
            return n;
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
    }
}
