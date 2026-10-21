package incident.tracking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

public final class TokenID {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final String tokenId;
    private final String employeeId;
    private final String department;
    private final String issueType;
    private final String designation;
    private final String description;

    public TokenID(Incident incident, issue reportedIssue, int issueNumber) {
        Objects.requireNonNull(incident, "incident must not be null");
        Objects.requireNonNull(reportedIssue, "reportedIssue must not be null");
        if (issueNumber < 1) {
            throw new IllegalArgumentException("issueNumber must be greater than zero");
        }

        this.employeeId = incident.getEmployeeID();
        this.department = incident.getDepartment();
        this.issueType = reportedIssue.getIssueType();
        this.designation = incident.getDesignation();
        this.description = reportedIssue.getDescription();
        this.tokenId = createToken(incident, reportedIssue, issueNumber);
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getDepartment() {
        return department;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getDesignation() {
        return designation;
    }

    public String getDescription() {
        return description;
    }

    private static String createToken(Incident incident, issue reportedIssue, int issueNumber) {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, incident.getDesignation());
            update(digest, incident.getEmployeeID());
            update(digest, incident.getDepartment());
            update(digest, reportedIssue.getIssueType());
            update(digest, reportedIssue.getIssueId());
            update(digest, Integer.toString(issueNumber));
            digest.update(nonce);

            byte[] tokenBytes = digest.digest();
            StringBuilder token = new StringBuilder("TKN");
            token.append(String.format("%04d", issueNumber)).append('-');
            for (int i = 0; i < 12; i++) {
                token.append(HEX[(tokenBytes[i] >>> 4) & 0x0f]);
                token.append(HEX[tokenBytes[i] & 0x0f]);
            }
            return token.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
