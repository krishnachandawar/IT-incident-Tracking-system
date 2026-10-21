package ticketing;

public final class EstimatedResolution {
    private Integer estimatedMinutes;

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        if (estimatedMinutes < 0) {
            throw new IllegalArgumentException("Estimated minutes must not be negative.");
        }
        this.estimatedMinutes = estimatedMinutes;
    }

    public String getDisplayValue() {
        if (estimatedMinutes == null) {
            return "Not provided";
        }
        int hours = estimatedMinutes / 60;
        int minutes = estimatedMinutes % 60;
        if (hours == 0) {
            return minutes + " minutes";
        }
        if (minutes == 0) {
            return hours + " hours";
        }
        return hours + " hours " + minutes + " minutes";
    }
}
