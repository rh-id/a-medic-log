package m.co.rh.id.a_medic_log.app.provider.component;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

/**
 * Shared one-line text formatting for the export renderers of this package
 * (the spreadsheet, CSV archive and PDF report components).
 * Package-private on purpose, this is not part of the public API.
 */
final class ExportFormatUtils {

    private ExportFormatUtils() {
    }

    /**
     * Format a date time as ISO date string, ex: 2026-09-02T08:05:00+0700
     *
     * @param date the date to format
     * @return ISO date string, or empty string when date is null
     */
    static String formatIsoDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(date);
    }

    /**
     * Build a single line human readable summary of a medicine reminder,
     * ex: "Mon,Tue 08:00 — take 1 pill"
     *
     * @param medicineReminder the reminder to summarize
     * @return reminder summary string
     */
    static String buildReminderSummary(MedicineReminder medicineReminder) {
        if (medicineReminder == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        LinkedHashSet<Integer> reminderDays = medicineReminder.reminderDays;
        if (reminderDays != null && !reminderDays.isEmpty()) {
            String[] shortWeekDays = DateFormatSymbols.getInstance().getShortWeekdays();
            boolean firstDay = true;
            for (Integer dayOfWeek : reminderDays) {
                if (dayOfWeek == null || dayOfWeek < Calendar.SUNDAY || dayOfWeek > Calendar.SATURDAY) {
                    continue;
                }
                if (!firstDay) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(shortWeekDays[dayOfWeek]);
                firstDay = false;
            }
            if (!firstDay) {
                stringBuilder.append(" ");
            }
        }
        if (medicineReminder.startDateTime != null) {
            stringBuilder.append(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(medicineReminder.startDateTime));
        }
        if (medicineReminder.message != null && !medicineReminder.message.isEmpty()) {
            stringBuilder.append(" — ").append(medicineReminder.message);
        }
        return stringBuilder.toString();
    }
}
