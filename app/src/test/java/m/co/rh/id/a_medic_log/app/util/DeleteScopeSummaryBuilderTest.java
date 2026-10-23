package m.co.rh.id.a_medic_log.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DeleteScopeSummaryBuilderTest {

    private static DeleteScopeSummaryBuilder.Category category(int count, String label) {
        return new DeleteScopeSummaryBuilder.Category(count, label);
    }

    @Test
    public void zeroCountCategories_areDropped() {
        // Resources.getQuantityString never returns an empty label, so a
        // childless note yields "0 medicines" style labels for every category
        String summary = DeleteScopeSummaryBuilder.buildScopeSummary(
                category(0, "0 medicines"),
                category(0, "0 reminders"),
                category(0, "0 attachments"),
                category(0, "0 intake records"));
        assertNull(summary);
    }

    @Test
    public void allZero_returnsNullForTerseFallback() {
        assertNull(DeleteScopeSummaryBuilder.buildScopeSummary());
        assertNull(DeleteScopeSummaryBuilder.buildScopeSummary((DeleteScopeSummaryBuilder.Category[]) null));
        assertNull(DeleteScopeSummaryBuilder.buildScopeSummary(category(0, "0 notes")));
    }

    @Test
    public void singleNonZeroCategory_returnsItUnchanged() {
        assertEquals("2 medicines",
                DeleteScopeSummaryBuilder.buildScopeSummary(category(2, "2 medicines")));
    }

    @Test
    public void zeroCategoriesBetweenNonZero_areDropped() {
        assertEquals("2 medicines \u00B7 8 attachments",
                DeleteScopeSummaryBuilder.buildScopeSummary(
                        category(2, "2 medicines"),
                        category(0, "0 reminders"),
                        category(8, "8 attachments")));
    }

    @Test
    public void multipleNonZeroCategories_joinedInCanonicalOrder() {
        String summary = DeleteScopeSummaryBuilder.buildScopeSummary(
                category(2, "2 medicines"),
                category(3, "3 reminders"),
                category(8, "8 attachments"),
                category(84, "84 intake records"));
        assertEquals("2 medicines \u00B7 3 reminders \u00B7 8 attachments \u00B7 84 intake records",
                summary);
    }

    @Test
    public void typicalNoteCounts_withoutMedicines() {
        // a note owning only attachments and intakes yields a short summary
        String summary = DeleteScopeSummaryBuilder.buildScopeSummary(
                category(0, "0 medicines"),
                category(0, "0 reminders"),
                category(1, "1 attachment"),
                category(1, "1 intake record"));
        assertEquals("1 attachment \u00B7 1 intake record", summary);
    }
}
