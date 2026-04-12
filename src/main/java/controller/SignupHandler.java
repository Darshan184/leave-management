package controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LeaveManagement;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Map;

public class SignupHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbTable<LeaveManagement> table;
    private final ObjectMapper mapper = new ObjectMapper();

    public SignupHandler() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.create()).build();
        this.table = enhancedClient.table(System.getenv("TABLE_NAME"),
                TableSchema.fromBean(LeaveManagement.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> body = mapper.readValue(input.getBody(), Map.class);
            String email = body.get("email");
            String password = body.get("password");
            String name = body.get("name");
            String approverEmail = body.get("approverEmail");
            //Check if user already exists
            LeaveManagement existing = table.getItem(Key.builder()
                    .partitionValue("User#" +email).sortValue("User#" +email).build());
            if (existing != null) {
                return response(400, "User already exists");
            }

            // Hash Password and Create Profile Item
            LeaveManagement newUser = new LeaveManagement();
            newUser.setPk("User#" + email);
            newUser.setSk("User#" + email);
            newUser.setType("USER");
            newUser.setName(name);
            newUser.setEmail(email);
            newUser.setApproverEmail(approverEmail);
            newUser.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));

            table.putItem(newUser);

            return response(201, "User registered successfully");

        } catch (Exception e) {
            return response(500, "Registration error: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withBody("{\"message\":\"" + msg + "\"}");
    }
}