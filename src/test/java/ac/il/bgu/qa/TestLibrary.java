package ac.il.bgu.qa;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import ac.il.bgu.qa.errors.BookAlreadyBorrowedException;
import ac.il.bgu.qa.errors.BookNotBorrowedException;
import ac.il.bgu.qa.errors.BookNotFoundException;
import ac.il.bgu.qa.errors.NoReviewsFoundException;
import ac.il.bgu.qa.errors.NotificationException;
import ac.il.bgu.qa.errors.ReviewException;
import ac.il.bgu.qa.errors.ReviewServiceUnavailableException;
import ac.il.bgu.qa.errors.UserNotRegisteredException;
import ac.il.bgu.qa.services.DatabaseService;
import ac.il.bgu.qa.services.NotificationService;
import ac.il.bgu.qa.services.ReviewService;

public class TestLibrary {

    // Valid default values
    private static final String VALID_ISBN = "9780306406157";
    private static final String VALID_ISBN_WITH_HYPHENS = "978-0-306-40615-7";
    private static final String VALID_ISBN_WITH_CHECK_DIGIT_ZERO = "9780000000200";
    private static final String VALID_REAL_WORLD_ISBN = "9780134685991";
    private static final String VALID_TITLE = "Harry Potter and the Philosopher's Stone";
    private static final String VALID_AUTHOR = "J.K. Rowling";
    private static final String VALID_USER_ID = "123456789012";
    private static final String VALID_USER_NAME = "Israel Israeli";
    // Arrays.asList (and not List.of) keeps the file compatible with the Java 8 level set in pom.xml
    private static final List<String> VALID_REVIEWS = Arrays.asList("Great book", "Loved it");
    private static final String EXPECTED_NOTIFICATION = "Reviews for '" + VALID_TITLE + "':\nGreat book\nLoved it";

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

    // Console streams, saved so tests that capture output can restore them
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

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
        System.setOut(originalOut);
        System.setErr(originalErr);
        mocks.close();
    }

    // ==================== Shared test data ====================
    /**
     * Invalid ISBN inputs shared by every method that validates an ISBN. null
     * and "" are supplied separately by @NullAndEmptySource.
     */
    static Stream<String> invalidIsbns() {
        return Stream.of(
                "123456789012", // 12 digits: one below the required length
                "12345678901234", // 14 digits: one above the required length
                "97803064061570", // a valid ISBN followed by an extra digit
                "97803m6406157", // 13 characters, one of them a letter
                "978 0306406157", // a space as separator (only hyphens are removed)
                "9780306406158", // check digit one above the correct one (7)
                "9780306406156");    // check digit one below the correct one (7)
    }

    /**
     * Invalid user Id inputs shared by every method that validates a user Id.
     * null and "" are supplied separately by @NullAndEmptySource.
     */
    static Stream<String> invalidUserIds() {
        return Stream.of(
                "12345678901", // 11 digits: one below the required length
                "1234567890123", // 13 digits: one above the required length
                "12345678901a", // 12 characters, one of them a letter
                "123456 89012", // 12 characters, one of them a space
                "123456-89012");     // 12 characters, one of them a hyphen
    }

    // ==================== registerUser tests ====================
    @Test
    void GivenValidUnregisteredUser_WhenRegisterUser_ThenUserIsRegistered() {
        // Act
        library.registerUser(user);

        // Assert
        verify(databaseService).registerUser(VALID_USER_ID, user);
    }

    @Test
    void GivenNullUser_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Act + Assert
        assertRegisterUserFails(null, "Invalid user.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidUserIds")
    void GivenInvalidUserId_WhenRegisterUser_ThenThrowIllegalArgumentException(String invalidId) {
        // Arrange
        when(user.getId()).thenReturn(invalidId);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid user Id.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenInvalidUserName_WhenRegisterUser_ThenThrowIllegalArgumentException(String invalidName) {
        // Arrange
        when(user.getName()).thenReturn(invalidName);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid user name.");
    }

    @Test
    void GivenNullNotificationService_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Arrange
        when(user.getNotificationService()).thenReturn(null);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid notification service.");
    }

    @Test
    void GivenUserIdAlreadyExists_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act + Assert
        assertRegisterUserFails(user, "User already exists.");
    }

    /**
     * Calls registerUser, asserts it throws IllegalArgumentException with the
     * expected message, and asserts that nothing was registered in the
     * database.
     */
    private void assertRegisterUserFails(User userToRegister, String expectedMessage) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.registerUser(userToRegister));
        assertEquals(expectedMessage, exception.getMessage());
        verify(databaseService, never()).registerUser(any(), any());
    }

    // ==================== addBook tests ====================
    @Test
    void GivenNullBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Act + Assert
        assertAddBookFails(null, "Invalid book.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIsbns")
    void GivenInvalidIsbn_WhenAddBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Arrange
        when(book.getISBN()).thenReturn(invalidIsbn);

        // Act + Assert
        assertAddBookFails(book, "Invalid ISBN.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNullOrEmptyTitle_WhenAddBook_ThenThrowIllegalArgumentException(String title) {
        // Arrange
        when(book.getTitle()).thenReturn(title);

        // Act + Assert
        assertAddBookFails(book, "Invalid title.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "-nitzan", // starts with a special character
        "Nitzan Zagur*", // ends with an invalid character
        "Nitzan Zaguri2", // ends with a digit
        "Nitzan Za*guri", // invalid character in the middle
        "Nitz4n Zaguri", // digit in the middle
        "Nitzan Za&guri", // invalid character whose code is below the apostrophe
        "Nitzan\tZaguri", // tab instead of a space
        "N*itzan Zaguri", // invalid character right after the first letter
        "Nitzan Zagur*i", // invalid character right before the last letter
        "Nitz''an Zaguri", // two consecutive apostrophes
        "Nitzan Z..aguri", // two consecutive dots
        "Nitz--an Zaguri", // two consecutive hyphens
        "Nitzan  Zaguri"})   // two consecutive spaces
    void GivenInvalidAuthor_WhenAddBook_ThenThrowIllegalArgumentException(String invalidAuthor) {
        // Arrange
        when(book.getAuthor()).thenReturn(invalidAuthor);

        // Act + Assert
        assertAddBookFails(book, "Invalid author.");
    }

    @Test
    void GivenBorrowedBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        when(book.isBorrowed()).thenReturn(true);

        // Act + Assert
        assertAddBookFails(book, "Book with invalid borrowed state.");
    }

    @Test
    void GivenBookAlreadyExists_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act + Assert
        assertAddBookFails(book, "Book already exists.");
    }

    /**
     * Calls addBook, asserts it throws IllegalArgumentException with the
     * expected message, and asserts that nothing was saved in the database.
     */
    private void assertAddBookFails(Book bookToAdd, String expectedMessage) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.addBook(bookToAdd));
        assertEquals(expectedMessage, exception.getMessage());
        verify(databaseService, never()).addBook(any(), any());
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

    @Test
    void GivenRealWorldIsbn_WhenAddBook_ThenBookIsSavedInDatabase() {
        // Arrange: a second valid ISBN with a different digit pattern,
        // so a wrong checksum formula cannot accept both by coincidence
        when(book.getISBN()).thenReturn(VALID_REAL_WORLD_ISBN);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_REAL_WORLD_ISBN, book);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "A", // shortest possible name: a single letter
        "Nitzan Zaguri", // space between letters
        "Nitzan-Zaguri", // hyphen between letters
        "Nitzan.Zaguri", // dot between letters
        "Nit'zan Zaguri", // apostrophe between letters
        "\u00C9mile Zola"})  // non-English letter (E with acute accent)
    void GivenValidAuthor_WhenAddBook_ThenBookIsSavedInDatabase(String validAuthor) {
        // Arrange
        when(book.getAuthor()).thenReturn(validAuthor);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Nitzan 'Zaguri", // space then apostrophe
        "Nitzan' Zaguri", // apostrophe then space
        "Nitzan -Zaguri", // space then hyphen
        "Nitzan- Zaguri", // hyphen then space
        "Nitzan .Zaguri", // space then dot
        "Nitz-'an Zaguri"})  // hyphen then apostrophe
    void GivenDifferentAdjacentSpecialChars_WhenAddBook_ThenBookIsSavedInDatabase(String validAuthor) {
        // Arrange: only identical adjacent special characters are forbidden
        when(book.getAuthor()).thenReturn(validAuthor);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    // ==================== borrowBook tests ====================
    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIsbns")
    void GivenInvalidIsbn_WhenBorrowBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.borrowBook(invalidIsbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenBorrowBook_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidUserIds")
    void GivenInvalidUserId_WhenBorrowBook_ThenThrowIllegalArgumentException(String invalidUserId) {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.borrowBook(VALID_ISBN, invalidUserId));

        // Assert
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenUserNotRegistered_WhenBorrowBook_ThenThrowUserNotRegisteredException() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        UserNotRegisteredException exception = assertThrows(UserNotRegisteredException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

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
        BookAlreadyBorrowedException exception = assertThrows(BookAlreadyBorrowedException.class,
                () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));

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

    // ==================== returnBook tests ====================
    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIsbns")
    void GivenInvalidIsbn_WhenReturnBook_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.returnBook(invalidIsbn));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenReturnBook_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.returnBook(VALID_ISBN));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenBookNotBorrowed_WhenReturnBook_ThenThrowBookNotBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(false);

        // Act
        BookNotBorrowedException exception = assertThrows(BookNotBorrowedException.class,
                () -> library.returnBook(VALID_ISBN));

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

    // ==================== getBookByISBN tests ====================
    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIsbns")
    void GivenInvalidIsbn_WhenGetBookByISBN_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(invalidIsbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidUserIds")
    void GivenInvalidUserId_WhenGetBookByISBN_ThenThrowIllegalArgumentException(String invalidUserId) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(VALID_ISBN, invalidUserId));

        // Assert
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenGetBookByISBN_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenBookAlreadyBorrowed_WhenGetBookByISBN_ThenThrowBookAlreadyBorrowedException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        BookAlreadyBorrowedException exception = assertThrows(BookAlreadyBorrowedException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book was already borrowed!", exception.getMessage());
    }

    @Test
    void GivenBookAlreadyBorrowed_WhenGetBookByISBN_ThenDoNotNotifyUser() {
        // Arrange
        Library spyLibrary = spy(library);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(book.isBorrowed()).thenReturn(true);

        // Act
        assertThrows(BookAlreadyBorrowedException.class,
                () -> spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(spyLibrary, never()).notifyUserWithBookReviews(anyString(), anyString());
    }

    @Test
    void GivenValidIsbnAndUserId_WhenGetBookByISBN_ThenReturnBook() {
        // Arrange
        Library spyLibrary = spy(library);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        doNothing().when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Act
        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
    }

    @Test
    void GivenValidIsbnAndUserId_WhenGetBookByISBN_ThenNotifyUserWithBookReviews() {
        // Arrange
        Library spyLibrary = spy(library);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        doNothing().when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Act
        spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
    }

    @Test
    void GivenNotificationFails_WhenGetBookByISBN_ThenReturnBook() {
        // Arrange
        Library spyLibrary = spy(library);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        doThrow(new RuntimeException()).when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Act
        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
    }

    @Test
    void GivenNotificationFails_WhenGetBookByISBN_ThenPrintNotificationFailed() {
        // Arrange
        Library spyLibrary = spy(library);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        doThrow(new RuntimeException()).when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Act
        spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertEquals("Notification failed!" + System.lineSeparator(), outContent.toString());
    }

    // ==================== notifyUserWithBookReviews tests ====================
    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidIsbns")
    void GivenInvalidIsbn_WhenNotifyUserWithBookReviews_ThenThrowIllegalArgumentException(String invalidIsbn) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(invalidIsbn, VALID_USER_ID));

        // Assert
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidUserIds")
    void GivenInvalidUserId_WhenNotifyUserWithBookReviews_ThenThrowIllegalArgumentException(String invalidUserId) {
        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, invalidUserId));

        // Assert
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenBookNotFound_WhenNotifyUserWithBookReviews_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenUserNotFound_WhenNotifyUserWithBookReviews_ThenThrowUserNotRegisteredException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act
        UserNotRegisteredException exception = assertThrows(UserNotRegisteredException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("User not found!", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNoReviews_WhenNotifyUserWithBookReviews_ThenThrowNoReviewsFoundException(List<String> reviews) {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(reviews);

        // Act
        NoReviewsFoundException exception = assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("No reviews found!", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNoReviews_WhenNotifyUserWithBookReviews_ThenCloseReviewService(List<String> reviews) {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(reviews);

        // Act
        assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(reviewService).close();
    }

    @Test
    void GivenReviewServiceFails_WhenNotifyUserWithBookReviews_ThenThrowReviewServiceUnavailableException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new ReviewException("Review service failed"));

        // Act
        ReviewServiceUnavailableException exception = assertThrows(ReviewServiceUnavailableException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Review service unavailable!", exception.getMessage());
    }

    @Test
    void GivenReviewServiceFails_WhenNotifyUserWithBookReviews_ThenCloseReviewService() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new ReviewException("Review service failed"));

        // Act
        assertThrows(ReviewServiceUnavailableException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(reviewService).close();
    }

    @Test
    void GivenValidIsbnAndUserId_WhenNotifyUserWithBookReviews_ThenSendNotificationToUser() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user).sendNotification(EXPECTED_NOTIFICATION);
    }

    @Test
    void GivenValidIsbnAndUserId_WhenNotifyUserWithBookReviews_ThenCloseReviewService() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(reviewService).close();
    }

    @Test
    void GivenNotificationFailsFourTimes_WhenNotifyUserWithBookReviews_ThenNotificationSentOnFifthAttempt() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);
        NotificationException failure = new NotificationException("Notification service failed");
        doThrow(failure, failure, failure, failure).doNothing().when(user).sendNotification(anyString());

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(user, times(5)).sendNotification(EXPECTED_NOTIFICATION);
    }

    @Test
    void GivenNotificationFailsFourTimes_WhenNotifyUserWithBookReviews_ThenPrintRetryMessages() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);
        NotificationException failure = new NotificationException("Notification service failed");
        doThrow(failure, failure, failure, failure).doNothing().when(user).sendNotification(anyString());
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        String expectedOutput = String.join(System.lineSeparator(),
                "Notification failed! Retrying attempt 1/5",
                "Notification failed! Retrying attempt 2/5",
                "Notification failed! Retrying attempt 3/5",
                "Notification failed! Retrying attempt 4/5") + System.lineSeparator();

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertEquals(expectedOutput, errContent.toString());
    }

    @Test
    void GivenNotificationAlwaysFails_WhenNotifyUserWithBookReviews_ThenThrowNotificationException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);
        doThrow(new NotificationException("Notification service failed")).when(user).sendNotification(anyString());

        // Act
        NotificationException exception = assertThrows(NotificationException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        assertEquals("Notification failed!", exception.getMessage());
    }

    @Test
    void GivenNotificationAlwaysFails_WhenNotifyUserWithBookReviews_ThenStopAfterFiveAttempts() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);
        doThrow(new NotificationException("Notification service failed")).when(user).sendNotification(anyString());

        // Act
        assertThrows(NotificationException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(user, times(5)).sendNotification(EXPECTED_NOTIFICATION);
    }

    @Test
    void GivenNotificationFailsWithUnexpectedError_WhenNotifyUserWithBookReviews_ThenDoNotRetry() {
        // Arrange: only NotificationException triggers a retry; any other error is propagated at once
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(VALID_REVIEWS);
        doThrow(new IllegalStateException("Unexpected error")).when(user).sendNotification(anyString());

        // Act
        assertThrows(IllegalStateException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        // Assert
        verify(user, times(1)).sendNotification(EXPECTED_NOTIFICATION);
    }
}
