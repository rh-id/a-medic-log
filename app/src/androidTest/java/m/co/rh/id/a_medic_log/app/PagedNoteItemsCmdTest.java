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
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_medic_log.ExportImportTestProviderModule;
import m.co.rh.id.a_medic_log.app.provider.command.PagedNoteItemsCmd;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

/**
 * Instrumented tests of the {@link PagedNoteItemsCmd} pagination state
 * machine: the page doubling flow during search with the limit reset
 * semantics, the profile-scoped search routing (loadWithProfileId + search)
 * and the merged-source global ordering as seen through the command's
 * emissions. The SQL semantics behind these behaviors (scoping, the single
 * OR'd source merge, the global cap and ordering) are covered by the DAO
 * tests in the base module.
 */
@RunWith(AndroidJUnit4.class)
public class PagedNoteItemsCmdTest {

    private static final long AWAIT_TIMEOUT_MS = 30000;

    private Provider mProvider;
    private AppDatabase mAppDatabase;
    private ProfileDao mProfileDao;
    private NoteDao mNoteDao;
    private NoteTagDao mNoteTagDao;
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
        mNoteTagDao = mProvider.get(NoteTagDao.class);
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
    public void loadWithProfileId_thenSearch_emitsOnlyThatProfilesNotesNewestFirst()
            throws InterruptedException {
        // arrange: profile B owns a note matched by every search source,
        // profile A owns one content match and one tag-only match
        Profile profileA = insertProfile("Profile A", "about A");
        Profile profileB = insertProfile("Profile B", "about B");
        Note noteAContent = insertNote(profileA.id, "shared keyword content", minutesAfterBase(1));
        Note noteATag = insertNote(profileA.id, "plain A content", minutesAfterBase(2));
        insertNoteTag(noteATag.id, "shared keyword tag");
        Note noteB = insertNote(profileB.id, "shared keyword content", minutesAfterBase(3));
        insertNoteTag(noteB.id, "shared keyword tag");
        Medicine medicineB = insertMedicine(noteB.id, "shared keyword medicine", "desc B");
        insertMedicineIntake(medicineB.id, "shared keyword intake");

        PagedNoteItemsCmd pagedNoteItemsCmd = mProvider.get(PagedNoteItemsCmd.class);
        TestSubscriber<ArrayList<Note>> subscriber = new TestSubscriber<>();
        pagedNoteItemsCmd.getItemsFlow().subscribe(subscriber);
        pagedNoteItemsCmd.loadWithProfileId(profileA.id);
        awaitValues(subscriber, 2);
        pagedNoteItemsCmd.search("keyword");
        awaitValues(subscriber, 3);

        // assert: the search only returns profile A's notes (its content and
        // tag source), newest first
        ArrayList<Note> searchResult = subscriber.values().get(2);
        assertEquals(2, searchResult.size());
        assertEquals(noteATag.id, searchResult.get(0).id);
        assertEquals(noteAContent.id, searchResult.get(1).id);
        assertEquals(profileA.id, searchResult.get(0).profileId);
    }

    @Test
    public void search_mergesAllSources_globallyOrderedByEntryDateTimeDesc()
            throws InterruptedException {
        // arrange: four notes matched by different sources with distinct
        // entry dates plus two same-date notes proving the id tiebreaker;
        // noteTag matches both the content and the tag source
        Profile profile = insertProfile("Profile", "about");
        Note noteContent = insertNote(profile.id, "magicword in content", minutesAfterBase(10));
        Note noteMedicine = insertNote(profile.id, "plain content", minutesAfterBase(20));
        insertMedicine(noteMedicine.id, "magicword medicine", "desc");
        Note noteIntake = insertNote(profile.id, "another plain content", minutesAfterBase(30));
        Medicine intakeMedicine = insertMedicine(noteIntake.id, "Insulin", "desc");
        insertMedicineIntake(intakeMedicine.id, "took with magicword remark");
        Note noteTag = insertNote(profile.id, "magicword in content and tag", minutesAfterBase(40));
        insertNoteTag(noteTag.id, "magicword tag");
        // identical entry_date_time: the later-inserted (higher id) note wins
        Note tieOlder = insertNote(profile.id, "magicword tie note one", minutesAfterBase(1));
        Note tieNewer = insertNote(profile.id, "magicword tie note two", minutesAfterBase(1));
        assertTrue(tieOlder.id < tieNewer.id);

        PagedNoteItemsCmd pagedNoteItemsCmd = mProvider.get(PagedNoteItemsCmd.class);
        TestSubscriber<ArrayList<Note>> subscriber = new TestSubscriber<>();
        pagedNoteItemsCmd.getItemsFlow().subscribe(subscriber);
        pagedNoteItemsCmd.loadWithProfileId(profile.id);
        awaitValues(subscriber, 2);
        pagedNoteItemsCmd.search("magicword");
        awaitValues(subscriber, 3);

        // assert: six unique notes, newest first regardless of which source
        // matched, with equal entry dates ordered by id DESC
        ArrayList<Note> result = subscriber.values().get(2);
        assertEquals(6, result.size());
        assertEquals(noteTag.id, result.get(0).id);
        assertEquals(noteIntake.id, result.get(1).id);
        assertEquals(noteMedicine.id, result.get(2).id);
        assertEquals(noteContent.id, result.get(3).id);
        assertEquals(tieNewer.id, result.get(4).id);
        assertEquals(tieOlder.id, result.get(5).id);
        assertSortedByEntryDateTimeDesc(result);
    }

    @Test
    public void search_pageDoubling_duringSearch_andLimitResetSemantics()
            throws InterruptedException {
        // arrange: enough matching notes for a full doubled search page
        Profile profile = insertProfile("Profile", "about");
        int totalNotes = 45;
        for (int i = 0; i < totalNotes; i++) {
            insertNote(profile.id, "page keyword note " + i, minutesAfterBase(i));
        }
        PagedNoteItemsCmd pagedNoteItemsCmd = mProvider.get(PagedNoteItemsCmd.class);
        TestSubscriber<ArrayList<Note>> subscriber = new TestSubscriber<>();
        pagedNoteItemsCmd.getItemsFlow().subscribe(subscriber);
        pagedNoteItemsCmd.loadWithProfileId(profile.id);
        awaitValues(subscriber, 2);
        assertEquals(20, subscriber.values().get(1).size());

        // a new search is globally capped at the initial page size
        pagedNoteItemsCmd.search("page");
        awaitValues(subscriber, 3);
        assertEquals(20, subscriber.values().get(2).size());

        // scrolling to the end doubles the limit: the globally capped search
        // page now returns exactly 40 of the 45 matching rows
        pagedNoteItemsCmd.loadNextPage();
        awaitValues(subscriber, 4);
        ArrayList<Note> doubled = subscriber.values().get(3);
        assertEquals(40, doubled.size());
        assertSortedByEntryDateTimeDesc(doubled);

        // refresh during search re-runs with the current (doubled) limit;
        // a reset to 20 would return 20, distinguishable from 40
        pagedNoteItemsCmd.refresh();
        awaitValues(subscriber, 5);
        assertEquals(40, subscriber.values().get(4).size());

        // a new search resets the limit back to the initial page size
        pagedNoteItemsCmd.search("page");
        awaitValues(subscriber, 6);
        assertEquals(20, subscriber.values().get(5).size());

        // clearing the search falls back to the browse load with the reset limit
        pagedNoteItemsCmd.search("");
        awaitValues(subscriber, 7);
        assertEquals(20, subscriber.values().get(6).size());
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

    private static void assertSortedByEntryDateTimeDesc(List<Note> notes) {
        for (int i = 1; i < notes.size(); i++) {
            long previous = notes.get(i - 1).entryDateTime.getTime();
            long current = notes.get(i).entryDateTime.getTime();
            assertTrue("notes must be ordered by entry_date_time DESC, got "
                            + previous + " before " + current,
                    previous >= current);
        }
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

    private NoteTag insertNoteTag(Long noteId, String tag) {
        NoteTag noteTag = new NoteTag();
        noteTag.noteId = noteId;
        noteTag.tag = tag;
        noteTag.id = mNoteTagDao.insert(noteTag);
        return noteTag;
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
