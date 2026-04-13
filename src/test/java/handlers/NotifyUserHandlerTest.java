package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class NotifyUserHandlerTest {

    @Mock
    private SesV2Client mockSesClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private NotifyUserHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new NotifyUserHandler(mockSesClient, mapper);
    }

    @Test
    public void shouldNotifyEmployee_WithCorrectStatus() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("status", "APPROVED");

        Map<String, Object> data = new HashMap<>();
        data.put("email", "darshan@example.com");
        data.put("reason", "Family Trip");
        input.put("data", data);

        String result = handler.handleRequest(input, mockContext);

        assertEquals("Employee notified: APPROVED", result);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockSesClient, times(1)).sendEmail(captor.capture());

        SendEmailRequest capturedRequest = captor.getValue();

        assertEquals("darshan@example.com", capturedRequest.destination().toAddresses().get(0));

        String templateDataJson = capturedRequest.content().template().templateData();
        @SuppressWarnings("unchecked")
        Map<String, String> templateDataMap = mapper.readValue(templateDataJson, Map.class);

        assertEquals("APPROVED", templateDataMap.get("status"));
        assertEquals("Family Trip", templateDataMap.get("reason"));
    }

    @Test
    public void shouldFail_WhenInputIsMissingData() {
        Map<String, Object> invalidInput = new HashMap<>();
        invalidInput.put("status", "REJECTED");

        assertThrows(RuntimeException.class, () -> {
            handler.handleRequest(invalidInput, mockContext);
        });
    }
}