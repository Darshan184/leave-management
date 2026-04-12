package leaveHandlers;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import model.LeaveManagement;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.enhanced.dynamodb.*;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Set;
import java.time.LocalDate;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import java.time.format.DateTimeFormatter;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class ApplyLeaveHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbTable<LeaveManagement> table;
    private final ObjectMapper mapper = new ObjectMapper();//Object mapper to map json objects
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private final SfnClient sfn = SfnClient.create();
    private final String stateMachineArn;
    public ApplyLeaveHandler() {
        String tableName = System.getenv("TABLE_NAME");
        this.stateMachineArn=System.getenv("STATE_MACHINE_ARN");
        if (tableName == null)
            tableName = "LeaveManagement";
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.create())
                .build();
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(LeaveManagement.class));
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizerContext = request.getRequestContext().getAuthorizer();
            String email = (String) authorizerContext.get("email");
            LeaveManagement leave = mapper.readValue(request.getBody(), LeaveManagement.class);
            LocalDate fromDate = LocalDate.parse(leave.getFromDate(), FORMATTER);
            LocalDate toDate = LocalDate.parse(leave.getToDate(), FORMATTER);
            if (fromDate.isAfter(toDate)) {
                return response(400, "The from Date cannot be after the to Date");
            }
            LeaveManagement userProfile = table.getItem(Key.builder()
                    .partitionValue("USER#" + email)
                    .sortValue("USER#" + email)
                    .build());
            String leaveUuid = UUID.randomUUID().toString();
            leave.setSk("Leave#"+leaveUuid);
            leave.setPk("User#"+email);
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
        }catch(Exception e){
            return response(500,e.getMessage());
        }
    }
    private APIGatewayProxyResponseEvent response(int status,Object body){
        try{
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withBody(body!=null ? mapper.writeValueAsString(body): "");

        } catch (java.lang.Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal Server Error");
        }
    }
}
