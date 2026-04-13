package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LeaveManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginHandlerTest {

    @Mock
    private DynamoDbTable<LeaveManagement> mockTable;

    @Mock
    private Context mockContext;

    private LoginHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String testSecret = "my_super_secret_test_key_32_chars_long";

    @BeforeEach
    void setUp() {
        handler = new LoginHandler(mockTable, testSecret, mapper);
    }

    @Test
    public void shouldReturn200_WhenCredentialsAreValid() throws Exception {
        // Arrange
        String email = "darshan@test.com";
        String password = "Password123";
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(Map.of("email", email, "password", password)));

        LeaveManagement mockUser = new LeaveManagement();
        mockUser.setEmail(email);
        mockUser.setPasswordHash(hashedPassword);

        when(mockTable.getItem(any(Key.class))).thenReturn(mockUser);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = mapper.readValue(response.getBody(), Map.class);
        assertNotNull(responseBody.get("token"));
        verify(mockTable, times(1)).getItem(any(Key.class));
    }

    @Test
    public void shouldReturn401_WhenPasswordIsIncorrect() throws Exception {

        String email = "darshan@test.com";
        String correctPassword = "CorrectPass";
        String wrongPassword = "WrongPass";
        String hashedPassword = BCrypt.hashpw(correctPassword, BCrypt.gensalt());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(Map.of("email", email, "password", wrongPassword)));

        LeaveManagement mockUser = new LeaveManagement();
        mockUser.setPasswordHash(hashedPassword);

        when(mockTable.getItem(any(Key.class))).thenReturn(mockUser);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid credentials"));
    }

    @Test
    public void shouldReturn401_WhenUserDoesNotExist() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(Map.of("email", "ghost@test.com", "password", "any")));

        when(mockTable.getItem(any(Key.class))).thenReturn(null);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(401, response.getStatusCode());
    }
}

