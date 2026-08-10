package app.api;

import app.App;
import app.support.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jooby.Server;
import io.jooby.netty.NettyServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests: the whole application (Netty, Guice, Hikari, Flyway, Hibernate, Jackson) booted
 * against a real MySQL container, driven over HTTP.
 */
class LibraryApiIT {

    private static final String PRIDE_AND_PREJUDICE = "978-0141439518";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static Server server;
    private static String baseUrl;

    @BeforeAll
    static void bootApplication() throws IOException {
        TestDatabase.exportAsSystemProperties();

        var port = freePort();
        System.setProperty("server.port", Integer.toString(port));
        baseUrl = "http://localhost:" + port;

        server = new NettyServer();
        server.start(new App());
    }

    @AfterAll
    static void stopApplication() {
        if (server != null) {
            server.stop();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    @DisplayName("GET /library/books/{isbn} serialises the book using snake_case")
    void getBook() throws Exception {
        var response = get("/library/books/" + PRIDE_AND_PREJUDICE);

        assertEquals(200, response.statusCode());

        var book = JSON.readTree(response.body());
        assertEquals(PRIDE_AND_PREJUDICE, book.get("isbn").asText());
        assertEquals("Pride and Prejudice", book.get("title").asText());
        assertEquals("NOVEL", book.get("type").asText());
        assertEquals("Penguin Classics", book.get("publisher").get("name").asText());
        // renamed by the SNAKE_CASE naming strategy installed in App
        assertTrue(book.has("publication_date"));
        assertFalse(book.has("publicationDate"));
    }

    @Test
    @DisplayName("GET /library/books/{isbn} answers 404 for an unknown ISBN")
    void getBookUnknownIsbn() throws Exception {
        assertEquals(404, get("/library/books/does-not-exist").statusCode());
    }

    @Test
    @DisplayName("GET /library/search finds books by a partial title")
    void searchByPartialTitle() throws Exception {
        var response = get("/library/search?q=Harry");

        assertEquals(200, response.statusCode());
        var results = JSON.readTree(response.body());
        assertTrue(results.isArray());
        assertEquals(1, results.size());
        assertTrue(results.get(0).get("title").asText().startsWith("Harry Potter"));
    }

    @Test
    @DisplayName("GET /library/search without a term lists the whole catalogue")
    void searchWithoutTerm() throws Exception {
        var response = get("/library/search");

        assertEquals(200, response.statusCode());
        var results = JSON.readTree(response.body());
        // the sample data ships six items
        assertTrue(results.size() >= 6, "expected the sample catalogue, got " + results.size());
    }

    @Test
    @DisplayName("GET /library/books returns a page of matching books")
    void browseByTitle() throws Exception {
        var response = get("/library/books?title=Dune&page=1&size=10");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Dune"), "unexpected body: " + response.body());
    }

    /**
     * Documents current behaviour: {@code page} and {@code size} are bound with {@code intValue()}, so
     * omitting them is a bad request even though the controller has defaults for both.
     * See the {@code TODO} file: "Fix default value in QueryParam".
     */
    @Test
    @DisplayName("GET /library/books without page/size is a bad request")
    void browseWithoutPagingParams() throws Exception {
        assertEquals(400, get("/library/books?title=Dune").statusCode());
    }

    @Test
    @DisplayName("POST /library/books stores a new book that can then be read back")
    void addBook() throws Exception {
        var isbn = "IT-HTTP-0001";
        var body = """
                {
                  "isbn": "%s",
                  "title": "End To End Test Book",
                  "text": "Once upon an integration test...",
                  "type": "TEXTBOOK"
                }
                """.formatted(isbn);

        var created = post("/library/books", body);
        assertEquals(200, created.statusCode(), "unexpected body: " + created.body());

        var reloaded = get("/library/books/" + isbn);
        assertEquals(200, reloaded.statusCode());
        assertEquals("End To End Test Book", JSON.readTree(reloaded.body()).get("title").asText());
    }
}