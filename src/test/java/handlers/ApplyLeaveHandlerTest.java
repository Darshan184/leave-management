package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LeaveManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ApplyLeaveHandlerTest {

    @Mock
    private DynamoDbTable<LeaveManagement> mockTable;

    @Mock
    private SfnClient mockSfnClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger; // Added to fix NullPointerException

    private ApplyLeaveHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String mockSfnArn = "arn:aws:states:us-east-1:123456789012:stateMachine:LeaveApproval";

    @BeforeEach
    void setUp() {
        handler = new ApplyLeaveHandler(mockTable, mockSfnClient, mapper, mockSfnArn);
        lenient().when(mockContext.getLogger()).thenReturn(mockLogger);
    }



    @Test
    public void shouldReturn400_WhenFromDateIsAfterToDate() throws Exception {
            String body = "{\"email\": \"test@example.com\", \"fromDate\": \"2026-05-25\", \"toDate\": \"2026-05-20\", \"reason\": \"Valid reason length\"}";
            APIGatewayProxyRequestEvent request = createMockRequest(body, "test@example.com");
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("The from Date cannot be after the to Date"));
        }


    @Test
    public void shouldReturn200_WhenDataIsValid() throws Exception {
        String userEmail = "darshan@gmail.com";
        String approverEmail = "manager@gmail.com";
        String body = "{\"email\": \"darshan@gmail.com\", \"fromDate\": \"2026-05-20\", \"toDate\": \"2026-05-25\", \"reason\": \"Summer trip vacation\"}";

        APIGatewayProxyRequestEvent request = createMockRequest(body, userEmail);
        LeaveManagement mockProfile = new LeaveManagement();
        mockProfile.setApproverEmail(approverEmail);

        when(mockTable.getItem(any(Key.class))).thenReturn(mockProfile);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Leave applied"));
        verify(mockTable, times(1)).putItem(any(LeaveManagement.class));
        verify(mockSfnClient, times(1)).startExecution(any(StartExecutionRequest.class));
    }

    @Test
    public void shouldReturn404_WhenUserNotFound() throws Exception {
        String userEmail = "unknown@email.com";
        String body = "{\"email\": \"darshan@gmail.com\", \"fromDate\": \"2026-05-20\", \"toDate\": \"2026-05-25\", \"reason\": \"Summer trip vacation\"}";
        APIGatewayProxyRequestEvent request = createMockRequest(body, userEmail);
        when(mockTable.getItem(any(Key.class))).thenReturn(null);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("User profile not found"));
    }
    private APIGatewayProxyRequestEvent createMockRequest(String body, String email) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(body);

        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("email", email);

        APIGatewayProxyRequestEvent.ProxyRequestContext context = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        context.setAuthorizer(authorizer);

        request.setRequestContext(context);
        return request;
    }
}
