package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Template;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SendApprovalHandler implements RequestHandler<Map<String, Object>, String> {
    private final SesV2Client ses;
    private final ObjectMapper mapper;

    public SendApprovalHandler() {
        this(SesV2Client.create(), new ObjectMapper());
    }

    public SendApprovalHandler(SesV2Client sesClient, ObjectMapper objectMapper) {
        this.ses = sesClient;
        this.mapper = objectMapper;
    }
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            //Extract data from the event (Task Token and Leave Request Map)
            String taskToken = (String) event.get("taskToken");
            Map<String, Object> leaveData = (Map<String, Object>) event.get("leaveRequest");
            System.out.println(leaveData);
            // Construct the Base API dynamically
            String apiId = System.getenv("API_ID");
            String region = System.getenv("AWS_REGION");
            String stage = "Prod";
            String apiBaseUrl = String.format("https://%s.execute-api.%s.amazonaws.com/%s/approve",
                    apiId, region, stage);

            // Prepare Links and Data
            String encodedToken = URLEncoder.encode(taskToken, StandardCharsets.UTF_8);
            String leaveId = (String) leaveData.get("leaveId");
            String approverEmail = (String) leaveData.get("approverEmail");
            String sourceEmail = System.getenv("SOURCE_EMAIL");
            String employeeEmail = (String) leaveData.get("email");

            String approveLink = String.format("%s?token=%s&status=APPROVED&leaveId=%s&employeeEmail=%s",
                    apiBaseUrl, encodedToken, leaveId,employeeEmail);
            String rejectLink = String.format("%s?token=%s&status=REJECTED&leaveId=%s&employeeEmail=%s",
                    apiBaseUrl, encodedToken, leaveId,employeeEmail);

            Map<String, String> templateData = new HashMap<>();
            templateData.put("employeeEmail", (String) leaveData.get("email"));
            templateData.put("approveLink", approveLink);
            templateData.put("rejectLink", rejectLink);
            templateData.put("reason", (String) leaveData.get("reason"));

            //Build and Send SESv2 Email
            Template template = Template.builder()
                    .templateName("LeaveApprovalRequest-Darshan")
                    .templateData(mapper.writeValueAsString(templateData))
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .fromEmailAddress(sourceEmail)
                    .destination(d -> d.toAddresses(approverEmail))
                    .content(EmailContent.builder()
                            .template(template)
                            .build())
                    .build();

            ses.sendEmail(emailRequest);

            return "Approval email dispatched to approver: " + approverEmail;

        } catch (Exception e) {
            context.getLogger().log("SES Error: " + e.getMessage());
            throw new RuntimeException("Could not send approval email", e);
        }
    }
}