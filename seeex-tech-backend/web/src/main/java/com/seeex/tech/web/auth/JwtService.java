package com.seeex.tech.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final byte[] secret;
    private final ObjectMapper mapper;

    public JwtService(@Value("${auth.jwt.secret:change-me-in-production-to-a-32-byte-secret}") String secret,
                      ObjectMapper mapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    public String issue(String subject, String clientId, long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        return encode(Map.of("sub", subject, "client_id", clientId, "token_type", "USER_ACCESS",
                "jti", UUID.randomUUID().toString(), "iat", now, "exp", now + ttlSeconds));
    }

    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTime(parts[2], sign(parts[0] + "." + parts[1]))) throw new IllegalArgumentException();
            Map<String, Object> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Number exp = (Number) claims.get("exp");
            if (exp == null || exp.longValue() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            return claims;
        } catch (Exception ex) { throw new IllegalArgumentException("invalid jwt", ex); }
    }

    private String encode(Map<String, Object> claims) {
        try {
            String header = b64(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = b64(mapper.writeValueAsBytes(claims));
            return header + "." + payload + "." + sign(header + "." + payload);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String sign(String input) throws Exception { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return b64(mac.doFinal(input.getBytes(StandardCharsets.UTF_8))); }
    private String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private boolean constantTime(String a, String b) { return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII)); }
}
