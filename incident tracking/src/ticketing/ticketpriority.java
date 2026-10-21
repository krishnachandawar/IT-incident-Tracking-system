package ticketing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ticketpriority implements Comparator<ticketinfo> {
    @Override
    public int compare(ticketinfo first, ticketinfo second) {
        return Integer.compare(getPriorityScore(second), getPriorityScore(first));
    }

    public List<ticketinfo> prioritize(List<ticketinfo> tickets) {
        Objects.requireNonNull(tickets, "tickets must not be null");
        List<ticketinfo> prioritizedTickets = new ArrayList<>(tickets);
        Collections.sort(prioritizedTickets, this);
        return prioritizedTickets;
    }

    public int getPriorityScore(ticketinfo ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        int issueSeverity = getIssueSeverity(ticket);
        int designationRank = getDesignationRank(ticket.getDesignation());
        return issueSeverity * 60 + designationRank * 40;
    }

    private int getIssueSeverity(ticketinfo ticket) {
        String issueType = normalize(ticket.getIssueType());
        String description = normalize(ticket.getDescription());

        if (issueType.contains("security")) {
            return 5;
        }
        if (containsAny(description, "data loss", "data lost", "lost data", "data corruption")) {
            return 4;
        }
        if (issueType.contains("backup and recovery")) {
            return 4;
        }
        if (issueType.contains("hardware")) {
            return 3;
        }
        if (issueType.contains("network connectivity")
                || issueType.contains("database connection")) {
            return 2;
        }
        return 1;
    }

    private int getDesignationRank(String designation) {
        String normalizedDesignation = normalize(designation);
        int sdeIndex = normalizedDesignation.indexOf("sde");
        if (sdeIndex >= 0 && sdeIndex + 3 < normalizedDesignation.length()) {
            char levelCharacter = normalizedDesignation.charAt(sdeIndex + 3);
            boolean hasOneDigitLevel = !Character.isDigit(levelCharacter)
                    || sdeIndex + 4 == normalizedDesignation.length()
                    || !Character.isDigit(normalizedDesignation.charAt(sdeIndex + 4));
            if (Character.isDigit(levelCharacter) && hasOneDigitLevel) {
                int level = Character.digit(levelCharacter, 10);
                if (level >= 1 && level <= 5) {
                    return 6 - level;
                }
            }
        }
        return 0;
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}