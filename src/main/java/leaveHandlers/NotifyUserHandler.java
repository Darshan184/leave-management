package leaveHandlers; // Changed to match your SAM template casing

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper; // Added for JSON conversion
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Template;

import java.util.HashMap;
import java.util.Map;

public class NotifyUserHandler implements RequestHandler<Map<String, Object>, String> {
    private final SesV2Client ses = SesV2Client.create();
    private final ObjectMapper mapper = new ObjectMapper(); // FIXED: Added mapper

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // Extract status (APPROVED, REJECTED, or TIMED_OUT)
            String status = (String) input.get("status");
            Map<String, Object> userData = (Map<String, Object>) input.get("data");
            String userEmail = (String) userData.get("email");

            // Prepare data for the EMPLOYEE notification template
            Map<String, String> templateData = new HashMap<>();
            templateData.put("status", status); // Will show as "APPROVED", "REJECTED", etc.
            templateData.put("reason", (String) userData.get("reason"));

            // Build the Template object using the NEW template
            Template template = Template.builder()
                    .templateName("LeaveStatusNotification-Darshan")
                    .templateData(mapper.writeValueAsString(templateData))
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .fromEmailAddress(System.getenv("SOURCE_EMAIL"))
                    .destination(d -> d.toAddresses(userEmail))
                    .content(EmailContent.builder()
                            .template(template)
                            .build())
                    .build();

            ses.sendEmail(emailRequest);
            return "Employee notified: " + status;

        } catch (Exception e) {
            context.getLogger().log("SES Notification Error: " + e.getMessage());
            throw new RuntimeException("Could not notify user", e);
        }
    }
}