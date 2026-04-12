package auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AuthHandler implements RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {
    private static final String SECRET_STRING = System.getenv("jwt_secret");
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));

    @Override
    public IamPolicyResponse handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        String rawToken = event.getAuthorizationToken();
        String effect = "Deny";
        String principalId = "unauthorized";
        Map<String, Object> authContext = new HashMap<>();

        try {
            if (rawToken != null && rawToken.startsWith("Bearer ")) {
                String token = rawToken.substring(7);
                Claims claims = Jwts.parser()
                        .verifyWith(SECRET_KEY)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String email = claims.get("email", String.class);
                if (email != null) {
                    principalId = email;
                    effect = "Allow";
                    authContext.put("email", email);
                }
            }
        } catch (Exception e) {
            context.getLogger().log("JWT Error: " + e.getMessage());
        }

        IamPolicyResponse.Statement statement = IamPolicyResponse.Statement.builder()
                .withAction("execute-api:Invoke")
                .withEffect(effect)
                .withResource(Collections.singletonList(event.getMethodArn()))
                .build();

        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(IamPolicyResponse.PolicyDocument.builder()
                        .withVersion("2012-10-17")
                        .withStatement(Collections.singletonList(statement))
                        .build())
                .withContext(authContext)
                .build();
    }
}