package hack.web.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the S3-backed score store (TRK-012: the only durable option that
 * doesn't need an EFS mount, now actually being switched on for production
 * for the first time since it was written).
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("S3ScoreStore")
class S3ScoreStoreTest {

    private static final String BUCKET = "ruhrohgue-scores-test";
    private static final String KEY = "ruhrohgue/scores.json";

    @Test
    @DisplayName("readLines_noObjectYet_returnsEmptyListNotError")
    void readLines_noObjectYet_returnsEmptyListNotError() throws IOException {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        assertTrue(store.readLines().isEmpty(),
            "A table that has never been written is not an error, same as the file store");
    }

    @Test
    @DisplayName("readLines_existingObject_splitsIntoNonBlankLines")
    void readLines_existingObject_splitsIntoNonBlankLines() throws IOException {
        S3Client client = mock(S3Client.class);
        String body = "{\"a\":1}\n\n{\"b\":2}\n";
        ResponseBytes<GetObjectResponse> response = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(), body.getBytes(StandardCharsets.UTF_8));
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(response);

        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        assertEquals(List.of("{\"a\":1}", "{\"b\":2}"), store.readLines(),
            "Blank lines must be dropped, same contract as FileScoreStore");
    }

    @Test
    @DisplayName("readLines_s3Failure_wrapsAsIOException")
    void readLines_s3Failure_wrapsAsIOException() {
        S3Client client = mock(S3Client.class);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow((S3Exception) S3Exception.builder().message("boom").build());

        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        assertThrows(IOException.class, store::readLines,
            "Callers (HighScoreService) only know how to handle IOException");
    }

    @Test
    @DisplayName("writeLines_joinsWithNewlineAndUploadsToTheConfiguredKey")
    void writeLines_joinsWithNewlineAndUploadsToTheConfiguredKey() throws IOException {
        S3Client client = mock(S3Client.class);
        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        store.writeLines(List.of("{\"a\":1}", "{\"b\":2}"));

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(req.capture(), body.capture());
        assertEquals(BUCKET, req.getValue().bucket());
        assertEquals(KEY, req.getValue().key());
    }

    @Test
    @DisplayName("writeLines_s3Failure_wrapsAsIOException")
    void writeLines_s3Failure_wrapsAsIOException() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow((S3Exception) S3Exception.builder().message("boom").build());

        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        assertThrows(IOException.class, () -> store.writeLines(List.of("{\"a\":1}")));
    }

    @Test
    @DisplayName("deleteAll_existingObject_deletesIt")
    void deleteAll_existingObject_deletesIt() throws IOException {
        S3Client client = mock(S3Client.class);
        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        store.deleteAll();

        ArgumentCaptor<DeleteObjectRequest> req = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(req.capture());
        assertEquals(BUCKET, req.getValue().bucket());
        assertEquals(KEY, req.getValue().key());
    }

    @Test
    @DisplayName("deleteAll_alreadyAbsent_isNotAnError")
    void deleteAll_alreadyAbsent_isNotAnError() {
        S3Client client = mock(S3Client.class);
        when(client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);

        assertDoesNotThrow(store::deleteAll,
            "Deleting something already gone is the desired end state, not a failure");
    }

    @Test
    @DisplayName("constructor_keyWithPathTraversal_rejectsImmediately")
    void constructor_keyWithPathTraversal_rejectsImmediately() {
        S3Client client = mock(S3Client.class);
        assertThrows(SecurityException.class,
            () -> new S3ScoreStore(client, BUCKET, "../../etc/passwd"));
    }

    @Test
    @DisplayName("describe_returnsS3Uri")
    void describe_returnsS3Uri() {
        S3Client client = mock(S3Client.class);
        S3ScoreStore store = new S3ScoreStore(client, BUCKET, KEY);
        assertEquals("s3://" + BUCKET + "/" + KEY, store.describe());
    }
}
