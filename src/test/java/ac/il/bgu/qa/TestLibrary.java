package ac.il.bgu.qa;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import ac.il.bgu.qa.errors.BookAlreadyBorrowedException;
import ac.il.bgu.qa.errors.BookNotBorrowedException;
import ac.il.bgu.qa.errors.BookNotFoundException;
import ac.il.bgu.qa.errors.UserNotRegisteredException;
import ac.il.bgu.qa.services.DatabaseService;
import ac.il.bgu.qa.services.NotificationService;
import ac.il.bgu.qa.services.ReviewService;

public class TestLibrary {

    // Valid default values
    private static final String VALID_ISBN = "9780306406157";
    private static final String VALID_ISBN_WITH_HYPHENS = "978-0-306-40615-7";
    private static final String VALID_ISBN_WITH_CHECK_DIGIT_ZERO = "9780000000200";
    private static final String VALID_TITLE = "Harry Potter and the Philosopher's Stone";
    private static final String VALID_AUTHOR = "J.K. Rowling";
    private static final String VALID_USER_ID = "123456789012";
    private static final String VALID_USER_NAME = "Israel Israeli";

    // Test doubles (mocks)
    @Mock
    private DatabaseService databaseService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private Book book;
    @Mock
    private User user;

    private AutoCloseable mocks;
    private Library library;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        library = new Library(databaseService, reviewService);

        // Default: a valid book that is not borrowed
        when(book.getISBN()).thenReturn(VALID_ISBN);
        when(book.getTitle()).thenReturn(VALID_TITLE);
        when(book.getAuthor()).thenReturn(VALID_AUTHOR);
        when(book.isBorrowed()).thenReturn(false);

        // Default: a valid user
        when(user.getId()).thenReturn(VALID_USER_ID);
        when(user.getName()).thenReturn(VALID_USER_NAME);
        when(user.getNotificationService()).thenReturn(notificationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ====================================================== addBook tests ======================================================
    @Test
    void GivenNullBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(null));

        // Assert
        assertEquals("Invalid book.", exception.getMessage());
    }

    @Test
    void GivenNullIsbn_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        when(book.getISBN()).thenReturn(null);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789012", "12345678901234", "97803m6406157", "9780306406158"})
    void GivenInvalidIsbn_WhenAddBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Arrange
        when(book.getISBN()).thenReturn(invalidIsbn);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNullOrEmptyTitle_WhenAddBook_ThenThrowIllegalArgumentException(String title) {
        // Arrange
        when(book.getTitle()).thenReturn(title);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid title.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-nitzan", "Nitzan Zagur*", "Nitzan ka k** Zaguri", "Nitz''an Zaguri", "Nitzan Z..aguri", "Nitz--an Zaguri", "Nitzan  Zaguri"})
    void GivenInvalidAuthor_WhenAddBook_ThenThrowIllegalArgumentException(String invalidAuthor) {
        // Arrange
        when(book.getAuthor()).thenReturn(invalidAuthor);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Invalid author.", exception.getMessage());
    }

    @Test
    void GivenBorrowedBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        when(book.isBorrowed()).thenReturn(true);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Book with invalid borrowed state.", exception.getMessage());
    }

    @Test
    void GivenBookAlreadyExists_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.addBook(book));

        // Assert
        assertEquals("Book already exists.", exception.getMessage());
        verify(databaseService, never()).addBook(anyString(), any(Book.class));
    }

    @Test
    void GivenValidBook_WhenAddBook_ThenBookIsSavedInDatabase() {
        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    @Test
    void GivenIsbnWithHyphens_WhenAddBook_ThenBookIsSavedInDatabase() {
        // Arrange
        when(book.getISBN()).thenReturn(VALID_ISBN_WITH_HYPHENS);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN_WITH_HYPHENS, book);
    }

    @Test
    void GivenIsbnWithCheckDigitZero_WhenAddBook_ThenBookIsSavedInDatabase() {
        // Arrange
        when(book.getISBN()).thenReturn(VALID_ISBN_WITH_CHECK_DIGIT_ZERO);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN_WITH_CHECK_DIGIT_ZERO, book);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nitzan Zaguri", "Nitzan-Zaguri", "Nitzan.Zaguri", "Nit'zan Zaguri"})
    void GivenValidAuthor_WhenAddBook_ThenBookIsSavedInDatabase(String validAuthor) {
        // Arrange
        when(book.getAuthor()).thenReturn(validAuthor);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    // ====================================================== borrowBook tests ======================================================
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456789012", "12345678901234", "97803m6406157", "9780306406158"})
    void GivenInvalidIsbn_WhenBorrowBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.borrowBook(invalidIsbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenBorrowBook_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class, () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "1234567890123", "97803m640615"})
    void GivenInvalidUserId_WhenBorrowBook_ThenThrowIllegalArgumentException(String invalidUserId) {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.borrowBook(VALID_ISBN, invalidUserId));

        // Assert
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenUserNotRegistered_WhenBorrowBook_ThenThrowUserNotRegisteredException() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        UserNotRegisteredException exception = assertThrows(UserNotRegisteredException.class, () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("User not found!", exception.getMessage());
        verify(databaseService, never()).borrowBook(anyString(), anyString());
        verify(book, never()).borrow();
    }

    @Test
    void GivenBookAlreadyBorrowed_WhenBorrowBook_ThenThrowBookAlreadyBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        BookAlreadyBorrowedException exception = assertThrows(BookAlreadyBorrowedException.class, () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book is already borrowed!", exception.getMessage());
        verify(databaseService, never()).borrowBook(anyString(), anyString());
        verify(book, never()).borrow();
    }

    @Test
    void GivenValidBookAndUserId_WhenBorrowBook_ThenBookIsBorrowed() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(false);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        library.borrowBook(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(databaseService).borrowBook(VALID_ISBN, VALID_USER_ID);
        verify(book).borrow();
    }

    // ====================================================== returnBook tests ======================================================
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456789012", "12345678901234", "97803m6406157", "9780306406158"})
    void GivenInvalidIsbn_WhenReturnBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> library.returnBook(invalidIsbn));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenReturnBook_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class, () -> library.returnBook(VALID_ISBN));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenBookNotBorrowed_WhenReturnBook_ThenThrowBookNotBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(false);

        // Act
        BookNotBorrowedException exception = assertThrows(BookNotBorrowedException.class, () -> library.returnBook(VALID_ISBN));

        // Assert
        assertEquals("Book wasn't borrowed!", exception.getMessage());
        verify(databaseService, never()).returnBook(anyString());
        verify(book, never()).returnBook();
    }

    @Test
    void GivenBorrowedBook_WhenReturnBook_ThenBookIsReturned() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        library.returnBook(VALID_ISBN);

        // Assert
        verify(databaseService).returnBook(VALID_ISBN);
        verify(book).returnBook();
    }
}
