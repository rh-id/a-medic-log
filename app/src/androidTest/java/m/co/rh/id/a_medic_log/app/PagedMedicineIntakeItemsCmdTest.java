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
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicineIntakeItemsCmd;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

/**
 * Instrumented tests of the {@link PagedMedicineIntakeItemsCmd} medicine
 * scoping: with setMedicineId both the browse load and the description
 * search emit only that medicine's intakes. The SQL semantics behind the
 * scoping are covered by the MedicineIntakeDaoTest in the base module.
 */
@RunWith(AndroidJUnit4.class)
public class PagedMedicineIntakeItemsCmdTest {

    private static final long AWAIT_TIMEOUT_MS = 30000;

    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private MedicineDao mMedicineDao;
    private MedicineIntakeDao mMedicineIntakeDao;
    // fixed base in the past so seeded notes have distinct, ordered entry dates
    private long mBaseTime;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mProvider = Provider.createProvider(context, new ExportImportTestProviderModule());
        mAppDatabase = mProvider.get(AppDatabase.class);
        mProfileDao = mProvider.get(ProfileDao.class);
        mNoteDao = mProvider.get(NoteDao.class);
        mMedicineDao = mProvider.get(MedicineDao.class);
        mMedicineIntakeDao = mProvider.get(MedicineIntakeDao.class);
        mBaseTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
    }

    @After
    public void cleanup() {
        mAppDatabase.close();
        mProvider.dispose();
    }

    @Test
    public void setMedicineId_refresh_thenSearch_returnsOnlyThatMedicinesMatchingIntake()
            throws InterruptedException {
        // arrange: two medicines under one note, each with an intake that
        // matches the search term
        Profile profile = insertProfile("Profile", "about");
        Note note = insertNote(profile.id, "intake parent note", minutesAfterBase(1));
        Medicine medicine1 = insertMedicine(note.id, "Med One", "desc one");
        Medicine medicine2 = insertMedicine(note.id, "Med Two", "desc two");
        MedicineIntake intake1 = insertMedicineIntake(medicine1.id, "breakfast dose");
        insertMedicineIntake(medicine2.id, "breakfast snack");

        PagedMedicineIntakeItemsCmd pagedMedicineIntakeItemsCmd =
                mProvider.get(PagedMedicineIntakeItemsCmd.class);
        TestSubscriber<ArrayList<MedicineIntake>> subscriber = new TestSubscriber<>();
        pagedMedicineIntakeItemsCmd.getItemsFlow().subscribe(subscriber);
        pagedMedicineIntakeItemsCmd.setMedicineId(medicine1.id);
        pagedMedicineIntakeItemsCmd.refresh();
        awaitValues(subscriber, 2);
        // assert: the browse load is scoped by medicine_id when set
        ArrayList<MedicineIntake> browseResult = subscriber.values().get(1);
        assertEquals(1, browseResult.size());
        assertEquals(intake1.id, browseResult.get(0).id);
        assertEquals(medicine1.id, browseResult.get(0).medicineId);
        pagedMedicineIntakeItemsCmd.search("breakfast");
        awaitValues(subscriber, 3);

        // assert: the intake list search is scoped by medicine_id when set
        ArrayList<MedicineIntake> intakeResult = subscriber.values().get(2);
        assertEquals(1, intakeResult.size());
        assertEquals(intake1.id, intakeResult.get(0).id);
        assertEquals(medicine1.id, intakeResult.get(0).medicineId);
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

    private Note insertNote(Long profileId, String content, Date entryDateTime) {
        Note note = new Note();
        note.profileId = profileId;
        note.content = content;
        note.entryDateTime = entryDateTime;
        note.id = mNoteDao.insert(note);
        return note;
    }

    private Medicine insertMedicine(Long noteId, String name, String description) {
        Medicine medicine = new Medicine();
        medicine.noteId = noteId;
        medicine.name = name;
        medicine.description = description;
        medicine.id = mMedicineDao.insert(medicine);
        return medicine;
    }

    private MedicineIntake insertMedicineIntake(Long medicineId, String description) {
        MedicineIntake medicineIntake = new MedicineIntake();
        medicineIntake.medicineId = medicineId;
        medicineIntake.description = description;
        medicineIntake.takenDateTime = new Date();
        medicineIntake.id = mMedicineIntakeDao.insert(medicineIntake);
        return medicineIntake;
    }

    private Date minutesAfterBase(int minutes) {
        return new Date(mBaseTime + TimeUnit.MINUTES.toMillis(minutes));
    }
}
