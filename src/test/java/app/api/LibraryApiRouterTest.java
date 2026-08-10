package app.api;

import app.model.Book;
import app.model.BookType;
import app.repo.Library;
import io.jooby.Jooby;
import io.jooby.exception.BadRequestException;
import io.jooby.exception.NotFoundException;
import io.jooby.jackson.Jackson2Module;
import io.jooby.test.MockContext;
import io.jooby.test.MockRouter;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Route-level tests: they run the generated router ({@link LibraryApi_}) so URL patterns and
 * parameter binding are exercised for real, but without a server or a database.
 */
class LibraryApiRouterTest {

    private static final String ISBN = "978-0141439518";

    private Library library;
    private MockRouter router;

    @BeforeEach
    void setUp() {
        library = mock(Library.class);
        var controller = new LibraryApi(library);

        Jooby app = new Jooby() {
            {
                install(new Jackson2Module());
                mvc(new LibraryApi_(controller));
            }
        };

        router = new MockRouter(app);
    }

    @Test
    @DisplayName("GET /library/books/{isbn} binds the ISBN from the path")
    void getBookBindsIsbnFromPath() {
        var book = new Book(ISBN, "Pride and Prejudice", BookType.NOVEL);
        when(library.findBook(ISBN)).thenReturn(Optional.of(book));

        assertSame(book, router.get("/library/books/" + ISBN).value());
    }

    @Test
    @DisplayName("GET /library/books/{isbn} fails with 404 for an unknown ISBN")
    void getBookUnknownIsbn() {
        when(library.findBook("does-not-exist")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> router.get("/library/books/does-not-exist"));
    }

    @Test
    @DisplayName("GET /library/search binds the q parameter")
    void searchBindsQueryParam() {
        var expected = List.of(new Book("978-0747532743", "Harry Potter", BookType.NOVEL));
        when(library.searchBooks("%Harry%")).thenReturn(expected);

        assertEquals(expected, router.get("/library/search", queryString("q=Harry")).value());
    }

    @Test
    @DisplayName("GET /library/search works with no q parameter at all")
    void searchWithoutQueryParam() {
        when(library.searchBooks("%%")).thenReturn(List.of());

        assertEquals(List.of(), router.get("/library/search").value());
    }

    @Test
    @DisplayName("GET /library/books binds title, page and size")
    void browseBindsPagingParams() {
        when(library.findBooksByTitle(eq("Dune"), any())).thenReturn(emptyPage());

        router.get("/library/books", queryString("title=Dune&page=2&size=5"));

        var captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(library).findBooksByTitle(eq("Dune"), captor.capture());
        assertEquals(2, captor.getValue().page());
        assertEquals(5, captor.getValue().size());
    }

    /**
     * Documents current behaviour: the controller has defaults for {@code page}/{@code size}, but the
     * generated router binds them with {@code intValue()}, which rejects the request before the
     * controller runs. See the {@code TODO} file: "Fix default value in QueryParam".
     */
    @Test
    @DisplayName("GET /library/books without page/size is rejected before the defaults apply")
    void browseWithoutPagingParamsIsRejected() {
        assertThrows(BadRequestException.class,
                () -> router.get("/library/books", queryString("title=Dune")));
    }

    private static MockContext queryString(String queryString) {
        return new MockContext().setQueryString("?" + queryString);
    }

    @SuppressWarnings("unchecked")
    private static Page<Book> emptyPage() {
        return mock(Page.class);
    }
}