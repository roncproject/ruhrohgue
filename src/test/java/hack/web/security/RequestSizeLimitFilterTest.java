package hack.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the request-body size cap (TRK-08): {@code Content-Length} is
 * checked up front for a fast rejection, and the input stream is separately
 * wrapped to catch a client that lies about, omits, or streams past the
 * declared length.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("RequestSizeLimitFilter")
class RequestSizeLimitFilterTest {

    private static final int LIMIT = 16;

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);

    @Test
    @DisplayName("doFilter_bodyWithinLimit_passesThrough")
    void doFilter_bodyWithinLimit_passesThrough() throws Exception {
        byte[] body = "short".getBytes(StandardCharsets.UTF_8);
        HttpServletRequest req = requestWithBody(body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(any(), eq(res));
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("doFilter_declaredContentLengthOverLimit_rejectsWith413BeforeReading")
    void doFilter_declaredContentLengthOverLimit_rejectsWith413BeforeReading() throws Exception {
        byte[] body = new byte[LIMIT + 100];
        HttpServletRequest req = requestWithBody(body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        when(res.getWriter()).thenReturn(new PrintWriter(written));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(res).setStatus(413);
        verify(chain, never()).doFilter(any(), any());
        verify(req, never()).getInputStream();
        assertTrue(written.toString(StandardCharsets.UTF_8).contains("payload_too_large"));
    }

    @Test
    @DisplayName("doFilter_streamExceedsLimitWithNoDeclaredLength_readThrowsPastTheCap")
    void doFilter_streamExceedsLimitWithNoDeclaredLength_readThrowsPastTheCap() throws Exception {
        byte[] body = new byte[LIMIT + 100];
        // Content-Length unknown (-1): the header check alone can't catch this,
        // so the wrapped stream is what has to stop the read.
        HttpServletRequest req = requestWithBody(body, -1);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        ArgumentCaptor<ServletRequest> wrapped = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(wrapped.capture(), eq(res));

        ServletInputStream in = wrapped.getValue().getInputStream();
        assertThrows(IOException.class, () -> {
            byte[] buf = new byte[body.length];
            int total = 0, n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) != -1) {
                total += n;
            }
        });
    }

    private static HttpServletRequest requestWithBody(byte[] body, long declaredLength) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentLengthLong()).thenReturn(declaredLength);
        when(req.getInputStream()).thenReturn(new ServletInputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(body);
            @Override public boolean isFinished() { return delegate.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) { }
            @Override public int read() { return delegate.read(); }
            @Override public int read(byte[] b, int off, int len) { return delegate.read(b, off, len); }
        });
        return req;
    }
}
