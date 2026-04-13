package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LeaveManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SignupHandlerTest {

    @Mock
    private DynamoDbTable<LeaveManagement> mockTable;

    @Mock
    private Context mockContext;

    private SignupHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new SignupHandler(mockTable, mapper);
    }

    @Test
    public void shouldReturn201_WhenRegistrationIsSuccessful() throws Exception {
        Map<String, String> body = Map.of(
                "email", "newuser@test.com",
                "password", "Pass123!",
                "name", "New User",
                "approverEmail", "boss@test.com"
        );
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(body));
        when(mockTable.getItem(any(Key.class))).thenReturn(null);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("User registered successfully"));
        ArgumentCaptor<LeaveManagement> userCaptor = ArgumentCaptor.forClass(LeaveManagement.class);
        verify(mockTable).putItem(userCaptor.capture());

        LeaveManagement savedUser = userCaptor.getValue();
        assertEquals("newuser@test.com", savedUser.getEmail());
        assertNotNull(savedUser.getPasswordHash());
        assertNotEquals("Pass123!", savedUser.getPasswordHash()); // Ensure it's hashed
    }

    @Test
    public void shouldReturn400_WhenUserAlreadyExists() throws Exception {

        String email = "existing@test.com";
        Map<String, String> body = Map.of(
                "email", "existing@test.com",
                "password", "Password123!",
                "name", "Existing User",
                "approverEmail", "boss@test.com"
        );
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(body));

        when(mockTable.getItem(any(Key.class))).thenReturn(new LeaveManagement());

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("User already exists"));
        verify(mockTable, never()).putItem(any(LeaveManagement.class));
    }

    @Test
    public void shouldReturn500_WhenDatabaseErrorOccurs() throws Exception {
        Map<String, String> body = Map.of(
                "email", "error@test.com",
                "password", "Password123!",
                "name", "Error User",
                "approverEmail", "boss@test.com"
        );
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(mapper.writeValueAsString(body));
        when(mockTable.getItem(any(Key.class))).thenThrow(new RuntimeException("DynamoDB Down"));
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Registration error"));
    }
}

