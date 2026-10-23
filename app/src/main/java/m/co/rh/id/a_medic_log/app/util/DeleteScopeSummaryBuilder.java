package m.co.rh.id.a_medic_log.app.util;

/**
 * Pure helper to build the delete scope summary for the delete confirmation
 * dialog, e.g. "2 medicines · 3 reminders · 8 attachments · 84 intake records".
 * Android-free so the decision logic is unit-testable on the JVM.
 */
public final class DeleteScopeSummaryBuilder {

    private static final String SEPARATOR = " \u00B7 ";

    private DeleteScopeSummaryBuilder() {
    }

    /**
     * Joins the count categories (already in canonical display order) into a
     * single summary line. Categories with a count of zero or less are dropped
     * (a zero count means the cascade deletes nothing of that kind), when no
     * category remains the scope is empty and null is returned so the caller
     * can fall back to the terse message.
     *
     * @param categories count categories in canonical display order
     * @return the joined summary, or null when every category has a zero count
     */
    public static String buildScopeSummary(Category... categories) {
        if (categories == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Category category : categories) {
            if (category == null || category.count <= 0
                    || category.label == null || category.label.isEmpty()) {
                continue;
            }
            if (stringBuilder.length() > 0) {
                stringBuilder.append(SEPARATOR);
            }
            stringBuilder.append(category.label);
        }
        if (stringBuilder.length() == 0) {
            return null;
        }
        return stringBuilder.toString();
    }

    /**
     * A single pre-formatted count category of the delete scope
     */
    public static class Category {
        public final int count;
        public final String label;

        public Category(int count, String label) {
            this.count = count;
            this.label = label;
        }
    }
}
