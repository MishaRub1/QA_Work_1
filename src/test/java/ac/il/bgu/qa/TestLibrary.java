package ac.il.bgu.qa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
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
    private ReviewService reviewService;;
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
    
}