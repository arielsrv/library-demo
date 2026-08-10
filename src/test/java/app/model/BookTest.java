package app.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookTest {

    @Test
    @DisplayName("the convenience constructor fills in the columns the schema declares NOT NULL")
    void constructorFillsRequiredColumns() {
        var book = new Book("978-0441172719", "Dune", BookType.NOVEL);

        assertEquals("978-0441172719", book.getIsbn());
        assertEquals("Dune", book.getTitle());
        assertEquals(BookType.NOVEL, book.getType());
        // text is @Basic(optional = false) / NOT NULL, so it must never be left null
        assertNotNull(book.getText());
    }

    @Test
    @DisplayName("a new book starts with an empty, non-null author set")
    void authorsStartEmpty() {
        var book = new Book("978-0441172719", "Dune", BookType.NOVEL);

        assertNotNull(book.getAuthors());
        assertTrue(book.getAuthors().isEmpty());
    }
}