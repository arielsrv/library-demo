package app.api;

import app.model.Book;
import app.model.BookType;
import app.repo.Library;
import io.jooby.exception.NotFoundException;
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
 * Unit tests for the front desk controller: no database, no server, no JSON.
 * The repository is mocked so we only check the decisions the controller itself makes.
 */
class LibraryApiTest {

    private static final String ISBN = "978-0141439518";

    private Library library;
    private LibraryApi api;

    @BeforeEach
    void setUp() {
        library = mock(Library.class);
        api = new LibraryApi(library);
    }

    @Test
    @DisplayName("getBook returns the book found by the repository")
    void getBookReturnsBook() {
        var book = new Book(ISBN, "Pride and Prejudice", BookType.NOVEL);
        when(library.findBook(ISBN)).thenReturn(Optional.of(book));

        assertSame(book, api.getBook(ISBN));
    }

    @Test
    @DisplayName("getBook turns an unknown ISBN into a 404")
    void getBookThrowsNotFound() {
        when(library.findBook("nope")).thenReturn(Optional.empty());

        var failure = assertThrows(NotFoundException.class, () -> api.getBook("nope"));
        assertEquals(404, failure.getStatusCode().value());
    }

    @Test
    @DisplayName("searchBooks wraps the term in wildcards so partial titles match")
    void searchBooksWrapsTermInWildcards() {
        var expected = List.of(new Book(ISBN, "Harry Potter", BookType.NOVEL));
        when(library.searchBooks("%Harry%")).thenReturn(expected);

        assertEquals(expected, api.searchBooks("Harry"));
    }

    @Test
    @DisplayName("searchBooks with no term matches everything instead of failing")
    void searchBooksWithoutTerm() {
        when(library.searchBooks("%%")).thenReturn(List.of());

        assertEquals(List.of(), api.searchBooks(null));
    }

    @Test
    @DisplayName("getBooksByTitle falls back to page 1, size 20")
    void getBooksByTitleAppliesDefaults() {
        when(library.findBooksByTitle(eq("Dune"), any())).thenReturn(emptyPage());

        api.getBooksByTitle("Dune", 0, 0);

        var pageRequest = capturePageRequest("Dune");
        assertEquals(1, pageRequest.page());
        assertEquals(20, pageRequest.size());
    }

    @Test
    @DisplayName("getBooksByTitle honours the requested page and size")
    void getBooksByTitleHonoursExplicitPaging() {
        when(library.findBooksByTitle(eq("Dune"), any())).thenReturn(emptyPage());

        api.getBooksByTitle("Dune", 3, 5);

        var pageRequest = capturePageRequest("Dune");
        assertEquals(3, pageRequest.page());
        assertEquals(5, pageRequest.size());
    }

    @Test
    @DisplayName("getBooksByTitle ignores negative paging values")
    void getBooksByTitleIgnoresNegativePaging() {
        when(library.findBooksByTitle(eq("Dune"), any())).thenReturn(emptyPage());

        api.getBooksByTitle("Dune", -2, -7);

        var pageRequest = capturePageRequest("Dune");
        assertEquals(1, pageRequest.page());
        assertEquals(20, pageRequest.size());
    }

    @Test
    @DisplayName("addBook hands the new book to the repository and returns what was stored")
    void addBookDelegatesToRepository() {
        var incoming = new Book("978-1", "New Book", BookType.TEXTBOOK);
        var stored = new Book("978-1", "New Book", BookType.TEXTBOOK);
        when(library.add(incoming)).thenReturn(stored);

        assertSame(stored, api.addBook(incoming));
        verify(library).add(incoming);
    }

    private PageRequest capturePageRequest(String title) {
        var captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(library).findBooksByTitle(eq(title), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Page<Book> emptyPage() {
        return mock(Page.class);
    }
}