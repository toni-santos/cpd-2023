package utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA512Generator {
    public static String encrypt(String str) {
        try {
            MessageDigest algo = MessageDigest.getInstance("SHA-512");
            byte[] result = algo.digest(str.getBytes());
            BigInteger num = new BigInteger(1, result);

            return num.toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
