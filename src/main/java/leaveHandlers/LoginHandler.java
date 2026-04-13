package leaveHandlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import model.LeaveManagement;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import util.ValidatorUtil;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import DTO.LoginRequestDTO;
public class LoginHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbTable<LeaveManagement> table;
    private final ObjectMapper mapper;
    private final String jwtSecret;

    public LoginHandler() {
        this(
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(DynamoDbClient.create()).build()
                        .table(System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : "LeaveManagement",
                                TableSchema.fromBean(LeaveManagement.class)),
                System.getenv("jwt_secret"),
                new ObjectMapper()
        );
    }

    public LoginHandler(DynamoDbTable<LeaveManagement> table, String jwtSecret, ObjectMapper mapper) {
        this.table = table;
        this.jwtSecret = jwtSecret;
        this.mapper = mapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            //Extract the request body
            Map<String, String> body = mapper.readValue(input.getBody(), Map.class);
            String email = body.get("email");
            String password = body.get("password");
            LoginRequestDTO loginReq = mapper.readValue(input.getBody(), LoginRequestDTO.class);

            //Bean Validation (Annotations)
            String validationErrors = ValidatorUtil.validate(loginReq);
            if (validationErrors != null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\":\"Validation error\"}");

            }

            // Fetch User Profile from Single Table
            LeaveManagement user = table.getItem(Key.builder()
                    .partitionValue("User#" + email)
                    .sortValue("User#" + email).build());

            // Verify Credentials
            if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("{\"error\":\"Invalid credentials\"}");
            }

            //Generate JWT
            String token = Jwts.builder()
                    .setSubject(user.getEmail())
                    .claim("email", user.getEmail())
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                    .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                    .compact();

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(mapper.writeValueAsString(Map.of("token", token)));

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}

