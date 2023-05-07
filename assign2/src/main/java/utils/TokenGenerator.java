package utils;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {

    public static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
