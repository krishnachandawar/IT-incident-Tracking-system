package access;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    static String hash(String password, String encodedSalt) {
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        PBEKeySpec keySpec = new PBEKeySpec(
                password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(keySpec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not securely hash the password.", e);
        } finally {
            keySpec.clearPassword();
        }
    }

    static boolean matches(String password, String encodedSalt, String expectedHash) {
        byte[] actual = Base64.getDecoder().decode(hash(password, encodedSalt));
        byte[] expected = Base64.getDecoder().decode(expectedHash);
        return MessageDigest.isEqual(actual, expected);
    }
}
