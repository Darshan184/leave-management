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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApproveLeaveHandlerTest {

    @Mock
    private DynamoDbTable<LeaveManagement> mockTable;

    @Mock
    private SfnClient mockSfn;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private ApproveLeaveHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApproveLeaveHandler(mockTable, mockSfn, new ObjectMapper());
        lenient().when(mockContext.getLogger()).thenReturn(mockLogger);
    }

    @Test
    public void shouldApproveLeave_WhenRecordExists() {

        String email = "employee@test.com";
        String leaveId = "uuid-123";

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("leaveId", leaveId);
        queryParams.put("status", "APPROVED");
        queryParams.put("token", "task-token-xyz");
        queryParams.put("employeeEmail", email);
        request.setQueryStringParameters(queryParams);

        LeaveManagement mockLeave = new LeaveManagement();
        mockLeave.setEmail(email);
        mockLeave.setStatus("Pending");

        when(mockTable.getItem(any(Key.class))).thenReturn(mockLeave);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);


        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("successfully"));

        verify(mockTable).updateItem(mockLeave);
        verify(mockSfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
    }

    @Test
    public void shouldReturn404_WhenLeaveNotFound() {

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("leaveId", "invalid-id");
        queryParams.put("status", "REJECTED");
        queryParams.put("token", "token-123");
        queryParams.put("employeeEmail", "none@test.com");
        request.setQueryStringParameters(queryParams);

        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

        assertEquals(404, response.getStatusCode());
        verify(mockSfn, never()).sendTaskSuccess(any(SendTaskSuccessRequest.class));
    }

    @Test
    public void shouldReturn400_WhenParamsAreMissing() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(null);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing query parameters"));
    }
}