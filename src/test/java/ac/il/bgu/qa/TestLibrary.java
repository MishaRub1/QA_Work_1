package ac.il.bgu.qa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import ac.il.bgu.qa.services.DatabaseService;
import ac.il.bgu.qa.services.NotificationService;
import ac.il.bgu.qa.services.ReviewService;

public class TestLibrary {

    // Valid default values
    private static final String VALID_ISBN = "9780306406157";
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

    // ---------- registerUser ----------

    @Test
    public void GivenValidUnregisteredUser_WhenRegisterUser_ThenUserIsRegistered() {
        // Arrange: the user is valid and getUserById returns null by default,
        // meaning the user is not registered yet

        // Act
        library.registerUser(user);
        
        // Assert
        verify(databaseService).registerUser(VALID_USER_ID, user);
    }
    
    @Test
    public void GivenNullUser_WhenRegisterUser_ThenThrowInvalidUser() {
        assertRegisterUserFails(null, "Invalid user.");
    }
    
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345678901", "1234567890123", "12345678901a", "123456 89012", "123456-89012"})
    public void GivenInvalidUserId_WhenRegisterUser_ThenThrowInvalidUserId(String invalidId) {
        // Arrange
        when(user.getId()).thenReturn(invalidId);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid user Id.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void GivenInvalidUserName_WhenRegisterUser_ThenThrowInvalidUserName(String invalidName) {
        // Arrange
        when(user.getName()).thenReturn(invalidName);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid user name.");
    }

    @Test
    public void GivenNullNotificationService_WhenRegisterUser_ThenThrowInvalidNotificationService() {
        // Arrange
        when(user.getNotificationService()).thenReturn(null);

        // Act + Assert
        assertRegisterUserFails(user, "Invalid notification service.");
    }
    
    @Test
    public void GivenUserIdAlreadyExists_WhenRegisterUser_ThenThrowUserAlreadyExists() {
        // Arrange
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act + Assert
        assertRegisterUserFails(user, "User already exists.");
    }
    
    /**
     * Calls registerUser, asserts it throws IllegalArgumentException with the expected message,
     * and asserts that nothing was registered in the database.
     */
    private void assertRegisterUserFails(User userToRegister, String expectedMessage) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> library.registerUser(userToRegister));
        assertEquals(expectedMessage, exception.getMessage());
        verify(databaseService, never()).registerUser(any(), any());
    }
}