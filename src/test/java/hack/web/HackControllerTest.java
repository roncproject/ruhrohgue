package hack.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@code DELETE /dev/scores}.
 *
 * <p>Covers the admin-token gate added to the endpoint: a missing header, a
 * wrong header value, and the correct header, each asserted against the
 * actual HTTP response rather than the extracted helper method, since the
 * point of this suite is to catch a regression in how the controller wires
 * the header into the check — not to re-test {@link java.security.MessageDigest}.</p>
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@WebMvcTest(HackController.class)
@TestPropertySource(properties = "ruhrohgue.admin.token=test-secret-token-12345")
@DisplayName("HackController /dev/scores admin gate")
class HackControllerTest {

    private static final String VALID_TOKEN = "test-secret-token-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HighScoreService highScoreService;

    @MockBean
    private GameSession session;

    @Test
    @DisplayName("deleteScores_missingTokenHeader_returnsUnauthorized")
    void deleteScores_missingTokenHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/dev/scores"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(highScoreService);
    }

    @Test
    @DisplayName("deleteScores_wrongTokenHeader_returnsUnauthorized")
    void deleteScores_wrongTokenHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/dev/scores").header("X-Admin-Token", "not-the-right-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(highScoreService);
    }

    @Test
    @DisplayName("deleteScores_correctTokenHeader_clearsScoresAndReturnsOk")
    void deleteScores_correctTokenHeader_clearsScoresAndReturnsOk() throws Exception {
        mockMvc.perform(delete("/dev/scores").header("X-Admin-Token", VALID_TOKEN))
                .andExpect(status().isOk());

        verify(highScoreService).clearAll();
    }
}
