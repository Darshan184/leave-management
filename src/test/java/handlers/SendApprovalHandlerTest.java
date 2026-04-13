package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper; // ADDED
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SendApprovalHandlerTest {

    @Mock
    private SesV2Client mockSesClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private SendApprovalHandler handler;
    private final ObjectMapper mapper = new ObjectMapper(); // ADDED

    @BeforeEach
    void setUp() {
        handler = new SendApprovalHandler(mockSesClient, mapper);
    }

    @Test
    public void shouldSendEmailWithCorrectLinks() {
        Map<String, Object> event = new HashMap<>();
        event.put("taskToken", "test-token-123");

        Map<String, Object> leaveData = new HashMap<>();
        leaveData.put("leaveId", "uuid-001");
        leaveData.put("approverEmail", "boss@example.com");
        leaveData.put("email", "employee@example.com");
        leaveData.put("reason", "Vacation");

        event.put("leaveRequest", leaveData);
        String result = handler.handleRequest(event, mockContext);
        assertTrue(result.contains("boss@example.com"));
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockSesClient, times(1)).sendEmail(captor.capture());
        SendEmailRequest capturedRequest = captor.getValue();
        assertEquals("boss@example.com", capturedRequest.destination().toAddresses().get(0));
        assertNotNull(capturedRequest.content().template());
        String templateData = capturedRequest.content().template().templateData();

        assertTrue(templateData.contains("employee@example.com"));
        assertTrue(templateData.contains("status=APPROVED"));
        assertTrue(templateData.contains("token=test-token-123"));
    }

    @Test
    public void shouldThrowRuntimeException_WhenSesFails() {
        when(mockContext.getLogger()).thenReturn(mockLogger);
        when(mockSesClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(new RuntimeException("SES Down"));

        Map<String, Object> event = createSampleEvent();

        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, mockContext));
    }

    private Map<String, Object> createSampleEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("taskToken", "token");
        Map<String, Object> leaveData = new HashMap<>();
        leaveData.put("leaveId", "id");
        leaveData.put("approverEmail", "a@b.com");
        event.put("leaveRequest", leaveData);
        return event;
    }
}