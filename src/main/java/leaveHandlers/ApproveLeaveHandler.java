package leaveHandlers;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.enhanced.dynamodb.*;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Set;
import java.time.LocalDate;
import software.amazon.awssdk.services.sfn.*;
import java.time.format.DateTimeFormatter;
import model.LeaveManagement;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;


public class ApproveLeaveHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbTable<LeaveManagement> table;
    private final SfnClient sfn = SfnClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApproveLeaveHandler() {
        String tableName = System.getenv("TABLE_NAME");
        if (tableName == null) tableName = "LeaveManagement";

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.create())
                .build();

        this.table = enhancedClient.table(tableName, TableSchema.fromBean(LeaveManagement.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            //  Extract parameters from the Query String (from the SES email link)
            Map<String, String> queryParams = input.getQueryStringParameters();
            String leaveId = queryParams.get("leaveId");
            String status = queryParams.get("status"); // Expected: "Approved" or "Rejected"
            String taskToken = queryParams.get("token");
            String employeeEmail = queryParams.get("employeeEmail");
            context.getLogger().log((leaveId));
            context.getLogger().log((status));
            context.getLogger().log((employeeEmail));
            String partitionKey = "User#" + employeeEmail;
            String sortKey = "Leave#" + leaveId;
            context.getLogger().log("All Params: " + queryParams.toString());
            LeaveManagement leave = table.getItem(Key.builder()
                    .partitionValue(partitionKey)
                    .sortValue(sortKey)
                    .build());
            context.getLogger().log((partitionKey));
            context.getLogger().log((sortKey));
            context.getLogger().log((leave.toString()));
            if (leave == null) {
                return response(404, "Leave request not found");
            }

            leave.setStatus(status);
            table.updateItem(leave);

            String outputJson = String.format("{\"status\": \"%s\", \"email\": \"%s\"}", status, leave.getEmail());

            sfn.sendTaskSuccess(SendTaskSuccessRequest.builder()
                    .taskToken(taskToken)
                    .output(outputJson)
                    .build());

            return response(200, "Leave " + status + " successfully.");

        } catch (Exception e) {
            context.getLogger().log("Error in Approval: " + e.getMessage());
            return response(500, "Error processing approval: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withBody("{\"message\": \"" + message + "\"}");
    }
}