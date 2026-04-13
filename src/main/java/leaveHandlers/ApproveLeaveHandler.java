package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LeaveManagement;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.util.Map;

public class ApproveLeaveHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbTable<LeaveManagement> table;
    private final SfnClient sfn;
    private final ObjectMapper mapper;

    public ApproveLeaveHandler() {
        this(
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(DynamoDbClient.create())
                        .build()
                        .table(System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : "LeaveManagement",
                                TableSchema.fromBean(LeaveManagement.class)),
                SfnClient.create(),
                new ObjectMapper()
        );
    }

    public ApproveLeaveHandler(DynamoDbTable<LeaveManagement> table, SfnClient sfn, ObjectMapper mapper) {
        this.table = table;
        this.sfn = sfn;
        this.mapper = mapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            //Safe extraction of Query String Parameters
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) {
                return response(400, "Missing query parameters");
            }

            String leaveId = queryParams.get("leaveId");
            String status = queryParams.get("status");
            String taskToken = queryParams.get("token");
            String employeeEmail = queryParams.get("employeeEmail");

            //Validate required fields to prevent logic errors later
            if (leaveId == null || status == null || taskToken == null || employeeEmail == null) {
                return response(400, "Missing required parameters: leaveId, status, token, or employeeEmail");
            }

            String partitionKey = "User#" + employeeEmail;
            String sortKey = "Leave#" + leaveId;

            //Fetch from DynamoDB
            LeaveManagement leave = table.getItem(Key.builder()
                    .partitionValue(partitionKey)
                    .sortValue(sortKey)
                    .build());

            //Check for null BEFORE any logging or property access
            if (leave == null) {
                context.getLogger().log("404: Leave request not found for " + partitionKey + " / " + sortKey);
                return response(404, "Leave request not found");
            }

            //Perform updates
            leave.setStatus(status);
            table.updateItem(leave);

            //Resume Step Function workflow with safe JSON generation
            String outputJson = mapper.writeValueAsString(Map.of(
                    "status", status,
                    "email", employeeEmail
            ));

            sfn.sendTaskSuccess(SendTaskSuccessRequest.builder()
                    .taskToken(taskToken)
                    .output(outputJson)
                    .build());

            return response(200, "Leave " + status + " successfully.");

        } catch (Exception e) {
            context.getLogger().log("Error in ApproveLeaveHandler: " + e.getMessage());
            return response(500, "Error processing approval: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"message\": \"" + message + "\"}");
    }
}