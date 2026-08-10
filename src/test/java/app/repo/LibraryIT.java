package app.repo;

import app.model.Address;
import app.model.Author;
import app.model.Book;
import app.model.BookType;
import app.model.Publisher;
import app.support.TestDatabase;
import jakarta.data.page.PageRequest;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Jakarta Data repository against a real MySQL.
 * <p>
 * These check what the generated {@code Library_} implementation actually sends to the database:
 * derived finders, HQL queries, paging, ordering and the eager relations. Fixture data comes from
 * {@code V0.0.1__Sample_Data.sql}.
 * </p>
 */
class LibraryIT {

    private static final String PRIDE_AND_PREJUDICE = "978-0141439518";

    private static SessionFactory sessionFactory;
    private static StatelessSession session;
    private static Library library;

    @BeforeAll
    static void bootDatabase() {
        var mysql = TestDatabase.startMigrated();

        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", mysql.getJdbcUrl())
                .applySetting("hibernate.connection.username", mysql.getUsername())
                .applySetting("hibernate.connection.password", mysql.getPassword())
                // Flyway owns the schema
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();

        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Book.class)
                .addAnnotatedClass(Author.class)
                .addAnnotatedClass(Publisher.class)
                .addAnnotatedClass(Address.class)
                .buildMetadata()
                .buildSessionFactory();

        session = sessionFactory.openStatelessSession();
        library = new Library_(session);
    }

    @AfterAll
    static void closeDatabase() {
        if (session != null) {
            session.close();
        }
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("findBook loads a book with its publisher and authors")
    void findBookLoadsRelations() {
        var book = library.findBook(PRIDE_AND_PREJUDICE).orElseThrow();

        assertEquals("Pride and Prejudice", book.getTitle());
        assertEquals(BookType.NOVEL, book.getType());
        assertEquals(1813, book.getPublicationDate().getYear());

        // @ManyToOne(EAGER)
        assertNotNull(book.getPublisher());
        assertEquals("Penguin Classics", book.getPublisher().name);

        // @ManyToMany(EAGER) through the Author_Book join table
        assertEquals(List.of("Jane Austen"), book.getAuthors().stream().map(a -> a.name).toList());
    }

    @Test
    @DisplayName("findBook returns empty for an unknown ISBN")
    void findBookUnknownIsbn() {
        assertTrue(library.findBook("not-a-real-isbn").isEmpty());
    }

    @Test
    @DisplayName("findAuthor looks an author up by SSN")
    void findAuthorBySsn() {
        var author = library.findAuthor("777-88-999").orElseThrow();

        assertEquals("Frank Herbert", author.name);
        assertEquals("Tacoma", author.address.city);
    }

    @Test
    @DisplayName("searchBooks matches a partial title")
    void searchBooksMatchesPartialTitle() {
        var titles = library.searchBooks("%Harry%").stream().map(Book::getTitle).toList();

        assertEquals(1, titles.size());
        assertTrue(titles.getFirst().startsWith("Harry Potter"));
    }

    @Test
    @DisplayName("searchBooks sorts results by title")
    void searchBooksSortsByTitle() {
        var titles = library.searchBooks("%").stream().map(Book::getTitle).toList();

        assertFalse(titles.isEmpty());
        var sorted = new ArrayList<>(titles);
        sorted.sort(Comparator.naturalOrder());
        assertEquals(sorted, titles);
    }

    @Test
    @DisplayName("searchBooks returns nothing when the term matches no title")
    void searchBooksWithoutMatches() {
        assertTrue(library.searchBooks("%no-such-title%").isEmpty());
    }

    @Test
    @DisplayName("findBooksByTitle returns a page with total count information")
    void findBooksByTitleReturnsPage() {
        var page = library.findBooksByTitle("Dune", PageRequest.ofPage(1).size(20));

        assertEquals(1, page.numberOfElements());
        assertEquals(1, page.totalElements());
        assertEquals("Dune", page.content().getFirst().getTitle());
    }

    @Test
    @DisplayName("findBooksByTitle splits results into pages")
    void findBooksByTitleSplitsPages() {
        var firstPage = library.findBooksByTitle("Dune", PageRequest.ofPage(1).size(1));
        assertEquals(1, firstPage.numberOfElements());

        var secondPage = library.findBooksByTitle("Dune", PageRequest.ofPage(2).size(1));
        assertEquals(0, secondPage.numberOfElements());
    }

    @Test
    @DisplayName("findBooksByTitle returns an empty page for an unknown title")
    void findBooksByTitleUnknownTitle() {
        var page = library.findBooksByTitle("No Such Title", PageRequest.ofPage(1).size(10));

        assertEquals(0, page.numberOfElements());
        assertTrue(page.content().isEmpty());
    }

    @Test
    @DisplayName("findRecentBookTitles filters by publication year")
    void findRecentBookTitlesFiltersByYear() {
        var recent = library.findRecentBookTitles(2023);

        assertTrue(recent.contains("Time Magazine: October 2023"));
        assertTrue(recent.contains("New England Journal of Medicine Vol 389"));
        // published in 1813
        assertFalse(recent.contains("Pride and Prejudice"));
    }

    @Test
    @DisplayName("a book can be inserted, read back and removed again")
    void insertReadAndRemoveBook() {
        var isbn = "IT-INSERT-0001";
        var book = new Book(isbn, "Repository Integration Test", BookType.TEXTBOOK);

        inTransaction(() -> library.add(book));
        try {
            var stored = library.findBook(isbn).orElseThrow();
            assertEquals("Repository Integration Test", stored.getTitle());
            assertEquals(BookType.TEXTBOOK, stored.getType());
        } finally {
            inTransaction(() -> library.remove(book));
        }

        assertTrue(library.findBook(isbn).isEmpty());
    }

    @Test
    @DisplayName("changes to an author are persisted")
    void updateAuthor() {
        var ssn = "555-66-777";
        var author = library.findAuthor(ssn).orElseThrow();
        var original = author.address;

        var moved = new Address();
        moved.street = "76 Sandfield Road";
        moved.city = "Headington";
        moved.zip = "OX3 7RJ";
        author.address = moved;

        inTransaction(() -> library.update(author));
        try {
            var reloaded = library.findAuthor(ssn).orElseThrow();
            assertEquals("Headington", reloaded.address.city);
            assertEquals("OX3 7RJ", reloaded.address.zip);
        } finally {
            author.address = original;
            inTransaction(() -> library.update(author));
        }
    }

    private static void inTransaction(Runnable work) {
        var tx = session.beginTransaction();
        try {
            work.run();
            tx.commit();
        } catch (RuntimeException failure) {
            tx.rollback();
            throw failure;
        }
    }
}