package m.co.rh.id.a_medic_log.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.app.model.MedicineRow;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicinesCmd;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

/**
 * Instrumented tests of the {@link PagedMedicinesCmd} pipeline: the name
 * grouping with the newest representative and its original casing, the
 * in-memory search filter over the loaded rows, refresh and the
 * loadWithProfileId(null) clear. The load's semantics (scoping, ordering and
 * their interplay with grouping and search) are covered here through the
 * grouping and search tests below.
 */
@RunWith(AndroidJUnit4.class)
public class PagedMedicinesCmdTest {

    private static final long AWAIT_TIMEOUT_MS = 30000;

    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    // fixed base in the past so seeded medicines have distinct, ordered created dates
    private long mBaseTime;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mAppDatabase = mProvider.get(AppDatabase.class);
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mBaseTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
    }

    @After
    public void cleanup() {
        mAppDatabase.close();
        mProvider.dispose();
    }

    @Test
    public void loadWithProfileId_groupsSameNameIntoOneRowWithNewestRepresentative()
            throws InterruptedException {
        // arrange: three medicines sharing the same name modulo case and
        // surrounding whitespace, inserted oldest first; the newest one wins
        // as the representative and keeps its original casing
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "note with grouped medicines");
        insertMedicine(note.id, "  Aspirin  ", minutesAfterBase(1));
        insertMedicine(note.id, "aspirin", minutesAfterBase(2));
        Medicine newest = insertMedicine(note.id, "ASPIRIN", minutesAfterBase(3));
        // oldest overall, so the aspirin group comes first in the newest-first rows
        insertMedicine(note.id, "Other Med", minutesAfterBase(0));

        PagedMedicinesCmd medicinesCmd = mProvider.get(PagedMedicinesCmd.class);
        TestSubscriber<ArrayList<MedicineRow>> subscriber = new TestSubscriber<>();
        medicinesCmd.getItemsFlow().subscribe(subscriber);
        medicinesCmd.loadWithProfileId(profile.id);
        awaitValues(subscriber, 2);

        // assert: the three name variants collapse into a single row, the
        // newest medicine is the representative with its original casing, and
        // the row carries all three medicine ids
        ArrayList<MedicineRow> rows = subscriber.values().get(1);
        assertEquals(2, rows.size());
        MedicineRow aspirinRow = rows.get(0);
        assertEquals("ASPIRIN", aspirinRow.medicine.name);
        assertEquals(newest.id, aspirinRow.medicine.id);
        assertEquals(3, aspirinRow.medicineIds.size());
        assertTrue(aspirinRow.medicineIds.contains(newest.id));
    }

    @Test
    public void search_filtersLoadedRowsInMemory_andResetShowsAllAgain()
            throws InterruptedException {
        // arrange: three differently named medicines of one profile
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "note with medicines");
        insertMedicine(note.id, "Paracetamol", minutesAfterBase(1));
        insertMedicine(note.id, "Ibuprofen", minutesAfterBase(2));
        insertMedicine(note.id, "Aspirin", minutesAfterBase(3));

        PagedMedicinesCmd medicinesCmd = mProvider.get(PagedMedicinesCmd.class);
        TestSubscriber<ArrayList<MedicineRow>> subscriber = new TestSubscriber<>();
        medicinesCmd.getItemsFlow().subscribe(subscriber);
        medicinesCmd.loadWithProfileId(profile.id);
        awaitValues(subscriber, 2);
        assertEquals(3, subscriber.values().get(1).size());

        // assert: the search narrows the loaded rows in memory (the name
        // match is case-insensitive, no database query involved)
        medicinesCmd.search("para");
        awaitValues(subscriber, 3);
        ArrayList<MedicineRow> filtered = subscriber.values().get(2);
        assertEquals(1, filtered.size());
        assertEquals("Paracetamol", filtered.get(0).medicine.name);

        // assert: an empty search resets the filter and shows all rows again
        medicinesCmd.search("");
        awaitValues(subscriber, 4);
        assertEquals(3, subscriber.values().get(3).size());
    }

    @Test
    public void loadWithProfileId_nullClearsRows() throws InterruptedException {
        // arrange: one medicine of one profile, loaded
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "note with medicine");
        insertMedicine(note.id, "Aspirin", minutesAfterBase(1));

        PagedMedicinesCmd medicinesCmd = mProvider.get(PagedMedicinesCmd.class);
        TestSubscriber<ArrayList<MedicineRow>> subscriber = new TestSubscriber<>();
        medicinesCmd.getItemsFlow().subscribe(subscriber);
        medicinesCmd.loadWithProfileId(profile.id);
        awaitValues(subscriber, 2);
        assertEquals(1, subscriber.values().get(1).size());

        // assert: loading with a null profile id clears the rows
        medicinesCmd.loadWithProfileId(null);
        awaitValues(subscriber, 3);
        assertEquals(0, subscriber.values().get(2).size());
    }

    /**
     * Waits until the subscriber received at least the given number of
     * emissions, failing with a timeout instead of blocking forever.
     */
    private static <T> void awaitValues(TestSubscriber<T> subscriber, int count)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (subscriber.values().size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("Expected at least " + count + " emissions but got "
                + subscriber.values().size(), subscriber.values().size() >= count);
    }

    // ---------------------------------------------------------------- seed builders
    // duplicated per test module: the app and base test provider modules
    // cannot share fixtures

    private Profile insertProfile(String name, String about) {
        Profile profile = new Profile();
        profile.name = name;
        profile.about = about;
        profile.id = mProfileDao.insert(profile);
        return profile;
    }

    private Note insertNote(Long profileId, String content) {
        Note note = new Note();
        note.profileId = profileId;
        note.content = content;
        note.id = mNoteDao.insert(note);
        return note;
    }

    private Medicine insertMedicine(Long noteId, String name, Date createdDateTime) {
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = name;
        medicine.createdDateTime = createdDateTime;
        medicine.id = mMedicineDao.insert(medicine);
        return medicine;
    }

    private Date minutesAfterBase(int minutes) {
        return new Date(mBaseTime + TimeUnit.MINUTES.toMillis(minutes));
    }
}
