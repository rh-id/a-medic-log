package m.co.rh.id.a_medic_log.app.provider.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.app.provider.command.AdherenceCalculator;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDoseOccurrenceDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Computes the {@link AdherenceReport}s of a single profile for one or more
 * date ranges ending today; the taken/missed/extra matching semantics are
 * documented on {@link AdherenceCalculator}. Each call first runs the
 * reminder catch-up so the persistent dose occurrence snapshot is current,
 * then serves every requested range from that shared data. An unknown or
 * deleted profile yields empty reports instead of failing.
 * This component is registered in the provider as a lazy singleton; resolve it
 * with {@link Provider#lazyGet}. Performs blocking database reads, background
 * thread only.
 */
public class AdherenceReportGenerator {
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<MedicineDoseOccurrenceDao> mMedicineDoseOccurrenceDao;
    private ProviderValue<ProfileDao> mProfileDao;
    private ProviderValue<MedicineRepository> mMedicineRepository;

    public AdherenceReportGenerator(Provider provider) {
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineDoseOccurrenceDao = provider.lazyGet(MedicineDoseOccurrenceDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mMedicineRepository = provider.lazyGet(MedicineRepository.class);
    }

    /**
     * Generate the adherence reports of a profile, one report per requested
     * range in the given order. The reminder catch-up, the profile lookup and
     * the medicine queries run once for the whole list, then each range is
     * calculated from the shared data.
     * This performs blocking database reads, must be called on a background thread.
     *
     * @param profileId id of the profile to report
     * @param rangeDays the report ranges in days (e.g. 7 or 30)
     * @return one report per requested range, by position
     */
    public ArrayList<AdherenceReport> generate(long profileId, List<Integer> rangeDays) {
        Date now = new Date();
        // record any occurrences the reminder commands have not yet
        // generated (e.g. doses of the last hours since the last app run),
        // the catch-up's single encompassing transaction serializes this
        // with the reminder commands
        mMedicineRepository.get().catchUpEnabledRemindersByProfile(profileId, now);
        // unknown profile (e.g. deleted in the meantime): report empty instead of failing
        Profile profile = mProfileDao.get().findProfileById(profileId);
        ArrayList<AdherenceReport> reports = new ArrayList<>(rangeDays.size());
        if (profile == null) {
            for (int i = 0; i < rangeDays.size(); i++) {
                reports.add(new AdherenceReport());
            }
            return reports;
        }
        List<Medicine> medicines = mMedicineDao.get().findMedicinesByProfileId(profileId);
        Map<Long, Medicine> medicineMap = new HashMap<>();
        for (Medicine medicine : medicines) {
            medicineMap.put(medicine.id, medicine);
        }
        for (Integer rangeDay : rangeDays) {
            long from = AdherenceCalculator.rangeStart(now, rangeDay);
            long to = AdherenceCalculator.rangeEnd(now);
            List<MedicineDoseOccurrence> occurrencesInRange = mMedicineDoseOccurrenceDao.get()
                    .findMedicineDoseOccurrencesByScheduledDateTimeBetween(from, to);
            // both bounds are widened by the matching window: an intake logged
            // outside the range (e.g. 00:30 the next day) can still be the
            // matching intake of an occurrence inside it
            List<MedicineIntake> intakesInRangeWidened = mMedicineIntakeDao.get()
                    .findMedicineIntakesByTakenDateTimeBetween(
                            from - AdherenceCalculator.WINDOW_MILLIS,
                            to + AdherenceCalculator.WINDOW_MILLIS);
            reports.add(AdherenceCalculator.calculate(profileId, rangeDay, now,
                    occurrencesInRange, intakesInRangeWidened, medicineMap, profile));
        }
        return reports;
    }

    /**
     * Generate the adherence report of a profile for a single range.
     * This performs blocking database reads, must be called on a background thread.
     *
     * @param profileId id of the profile to report
     * @param rangeDays the report range in days (e.g. 7 or 30)
     * @return the computed adherence report
     */
    public AdherenceReport generate(long profileId, int rangeDays) {
        return generate(profileId, Collections.singletonList(rangeDays)).get(0);
    }
}
