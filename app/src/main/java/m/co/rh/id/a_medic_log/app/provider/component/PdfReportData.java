package m.co.rh.id.a_medic_log.app.provider.component;

import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Value holder of the report data of a single profile for the PDF
 * medication report: its medicines with their reminders, the 7-day and
 * 30-day adherence summaries and the profile-scoped intake history of the
 * covered range. Assembled by
 * {@link m.co.rh.id.a_medic_log.app.provider.command.ExportPdfReportCmd}
 * and rendered by {@link PdfReportExporter}.
 */
public class PdfReportData {
    private static final String REMINDER_SEPARATOR = "; ";

    private final Profile mProfile;
    private final List<Medicine> mMedicines;
    private final Map<Long, List<MedicineReminder>> mReminderMap;
    private final AdherenceReport mAdherence7Days;
    private final AdherenceReport mAdherence30Days;
    private final List<MedicineIntake> mIntakesInRange;
    private final long mIntakeRangeStartMillis;
    private final long mIntakeRangeEndMillis;

    public PdfReportData(Profile profile, List<Medicine> medicines,
                         Map<Long, List<MedicineReminder>> reminderMap,
                         AdherenceReport adherence7Days, AdherenceReport adherence30Days,
                         List<MedicineIntake> intakesInRange,
                         long intakeRangeStartMillis, long intakeRangeEndMillis) {
        mProfile = profile;
        mMedicines = medicines;
        mReminderMap = reminderMap;
        mAdherence7Days = adherence7Days;
        mAdherence30Days = adherence30Days;
        mIntakesInRange = intakesInRange;
        mIntakeRangeStartMillis = intakeRangeStartMillis;
        mIntakeRangeEndMillis = intakeRangeEndMillis;
    }

    public Profile getProfile() {
        return mProfile;
    }

    public List<Medicine> getMedicines() {
        return mMedicines;
    }

    public Map<Long, List<MedicineReminder>> getReminderMap() {
        return mReminderMap;
    }

    public AdherenceReport getAdherence7Days() {
        return mAdherence7Days;
    }

    public AdherenceReport getAdherence30Days() {
        return mAdherence30Days;
    }

    public List<MedicineIntake> getIntakesInRange() {
        return mIntakesInRange;
    }

    public long getIntakeRangeStartMillis() {
        return mIntakeRangeStartMillis;
    }

    public long getIntakeRangeEndMillis() {
        return mIntakeRangeEndMillis;
    }

    /**
     * @return the medicine entity of the given id, or null when unknown
     */
    public Medicine findMedicineById(Long medicineId) {
        if (medicineId == null) {
            return null;
        }
        for (Medicine medicine : mMedicines) {
            if (medicineId.equals(medicine.id)) {
                return medicine;
            }
        }
        return null;
    }

    /**
     * Build a single line schedule summary of a medicine from its ENABLED
     * reminders, ex: "Mon,Tue 08:00 — take 1 pill; Wed 20:00 — take 2 pills".
     *
     * @param medicineId id of the medicine to summarize
     * @return the joined reminder summaries, or null when the medicine has no
     * enabled reminder (the exporter renders the localized "As needed (PRN)"
     * label for null)
     */
    public String getScheduleText(Long medicineId) {
        List<MedicineReminder> reminders = mReminderMap.get(medicineId);
        if (reminders == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (MedicineReminder medicineReminder : reminders) {
            if (medicineReminder == null
                    || medicineReminder.reminderEnabled == null
                    || !medicineReminder.reminderEnabled) {
                continue;
            }
            if (stringBuilder.length() > 0) {
                stringBuilder.append(REMINDER_SEPARATOR);
            }
            stringBuilder.append(ExportFormatUtils.buildReminderSummary(medicineReminder));
        }
        if (stringBuilder.length() == 0) {
            return null;
        }
        return stringBuilder.toString();
    }
}
