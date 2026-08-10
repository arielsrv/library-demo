-- --------------------------------------------------------
-- 1. Publisher Table
-- --------------------------------------------------------
-- Stores the publishing companies.
-- Created first because Books depend on Publishers.
CREATE TABLE IF NOT EXISTS Publisher
(
    id
    BIGINT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    name
    VARCHAR
(
    255
) NOT NULL
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;

-- --------------------------------------------------------
-- 2. Book Table
-- --------------------------------------------------------
-- Stores the physical items (Books, Magazines, etc).
-- 'publisher_id' links to the Publisher table.
CREATE TABLE IF NOT EXISTS Book
(
    isbn
    VARCHAR
(
    255
) NOT NULL PRIMARY KEY,
    title VARCHAR
(
    255
) NOT NULL,
    publicationDate DATE,

    -- @Lob maps to TEXT or LONGTEXT in MySQL for large content
    text LONGTEXT NOT NULL,

    -- Enum values stored as strings for readability
    type ENUM
(
    'NOVEL',
    'BIOGRAPHY',
    'TEXTBOOK',
    'MAGAZINE',
    'JOURNAL'
) NOT NULL,

    -- Foreign Key column
    publisher_id BIGINT,
    CONSTRAINT fk_book_publisher
    FOREIGN KEY
(
    publisher_id
)
    REFERENCES Publisher
(
    id
)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;

-- --------------------------------------------------------
-- 3. Author Table
-- --------------------------------------------------------
-- Stores author details.
-- The Address fields (street, city, zip) are embedded directly here.
CREATE TABLE IF NOT EXISTS Author
(
    ssn
    VARCHAR
(
    255
) NOT NULL PRIMARY KEY,
    name VARCHAR
(
    255
) NOT NULL,

    -- Embedded Address fields
    street VARCHAR
(
    255
),
    city VARCHAR
(
    255
),
    zip VARCHAR
(
    255
)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;

-- --------------------------------------------------------
-- 4. Author_Book (Join Table)
-- --------------------------------------------------------
-- Handles the Many-to-Many relationship between Authors and Books.
-- One author can write many books, and one book can have multiple authors.
CREATE TABLE IF NOT EXISTS Author_Book
(
    authors_ssn
    VARCHAR
(
    255
) NOT NULL,
    books_isbn VARCHAR
(
    255
) NOT NULL,
    PRIMARY KEY
(
    authors_ssn,
    books_isbn
),
    CONSTRAINT fk_ab_author
    FOREIGN KEY
(
    authors_ssn
)
    REFERENCES Author
(
    ssn
)
    ON DELETE CASCADE,
    CONSTRAINT fk_ab_book
    FOREIGN KEY
(
    books_isbn
)
    REFERENCES Book
(
    isbn
)
    ON DELETE CASCADE
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;
