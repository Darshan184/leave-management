/*package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import model.LeaveManagement;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import DTO.ApplyLeaveDTO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import util.ValidatorUtil;
public class ApplyLeaveHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbTable<LeaveManagement> table;
    private final SfnClient sfn;
    private final ObjectMapper mapper;
    private final String stateMachineArn;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public ApplyLeaveHandler() {
        this(
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(DynamoDbClient.create())
                        .build()
                        .table(System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : "LeaveManagement",
                                TableSchema.fromBean(LeaveManagement.class)),
                SfnClient.create(),
                new ObjectMapper(),
                System.getenv("STATE_MACHINE_ARN")
        );
    }

    public ApplyLeaveHandler(DynamoDbTable<LeaveManagement> table, SfnClient sfn, ObjectMapper mapper, String stateMachineArn) {
        this.table = table;
        this.sfn = sfn;
        this.mapper = mapper;
        this.stateMachineArn = stateMachineArn;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizerContext = request.getRequestContext().getAuthorizer();
            String email = (String) authorizerContext.get("email");
            ApplyLeaveDTO leaveReq = mapper.readValue(request.getBody(), ApplyLeaveDTO.class);

            //Bean Validation (Annotations)
            String validationErrors = ValidatorUtil.validate(leaveReq);
            if (validationErrors != null) {
                return response(400, "Validation Error: " + validationErrors);
            }
            LeaveManagement leave = mapper.readValue(request.getBody(), LeaveManagement.class);

            LocalDate fromDate = LocalDate.parse(leave.getFromDate(), FORMATTER);
            LocalDate toDate = LocalDate.parse(leave.getToDate(), FORMATTER);

            if (fromDate.isAfter(toDate)) {
                return response(400, "The from Date cannot be after the to Date");
            }

            // Standardized casing to "User#" to match your SK and other handlers
            String userKey = "User#" + email;
            LeaveManagement userProfile = table.getItem(Key.builder()
                    .partitionValue(userKey)
                    .sortValue(userKey)
                    .build());

            if (userProfile == null) {
                context.getLogger().log("User profile not found for email: " + email);
                return response(404, "User profile not found");
            }

            String leaveUuid = UUID.randomUUID().toString();
            leave.setPk("User#" + email);
            leave.setSk("Leave#" + leaveUuid);
            leave.setType("LEAVE");
            leave.setLeaveId(leaveUuid);
            leave.setEmail(email);
            leave.setFromDate(fromDate.toString());
            leave.setToDate(toDate.toString());
            leave.setApproverEmail(userProfile.getApproverEmail());
            leave.setStatus("Pending");

            table.putItem(leave);

            String payload = mapper.writeValueAsString(leave);
            sfn.startExecution(StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .input(payload)
                    .build());

            return response(200, "Leave applied");
        } catch (Exception e) {
            context.getLogger().log("Error in ApplyLeaveHandler: " + e.getMessage());
            return response(500, e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) {
        try {
            // Ensure strings are wrapped in a valid JSON object for API consistency
            String bodyContent = (body instanceof String) ?
                    mapper.writeValueAsString(Map.of("message", body)) :
                    mapper.writeValueAsString(body);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(bodyContent);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{\"message\":\"Internal Server Error\"}");
        }
    }
}
*/