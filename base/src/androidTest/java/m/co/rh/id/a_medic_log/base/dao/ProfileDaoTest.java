package m.co.rh.id.a_medic_log.base.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@RunWith(AndroidJUnit4.class)
public class ProfileDaoTest {
    private static final String TEST_DB = ProfileDaoTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    private AppDatabase mAppDatabase;

    @SuppressWarnings("deprecation")
    public ProfileDaoTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Before
    public void init() {
        mAppDatabase = createAppDb();
    }

    @After
    public void cleanup() {
        deleteAppDb();
    }

    private AppDatabase createAppDb() {
        return Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .addMigrations(DbMigration.getAll())
                .addTypeConverter(new LinkedHashSetConverter(null))
                .build();
    }

    private void deleteAppDb() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(TEST_DB);
    }

    private Profile insertProfile(String name) {
        Profile profile = new Profile();
        profile.name = name;
        profile.id = mAppDatabase.profileDao().insert(profile);
        return profile;
    }

    @Test
    public void findProfilesByIds_nullAndEmptyInput() {
        ProfileDao profileDao = mAppDatabase.profileDao();

        // null input returns empty non-null list
        List<Profile> nullResult = profileDao.findProfilesByIds(null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.size());

        // empty input returns empty non-null list
        List<Profile> emptyResult = profileDao.findProfilesByIds(new ArrayList<>());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.size());
    }

    @Test
    public void findProfilesByIds_deduplicatesInput() {
        ProfileDao profileDao = mAppDatabase.profileDao();
        Profile profileA = insertProfile("profile A");
        Profile profileB = insertProfile("profile B");
        Profile profileC = insertProfile("profile C");

        // query with duplicate ids
        List<Long> inputIds = new ArrayList<>();
        inputIds.add(profileA.id);
        inputIds.add(profileB.id);
        inputIds.add(profileC.id);
        inputIds.add(profileA.id);
        inputIds.add(profileB.id);
        List<Profile> result = profileDao.findProfilesByIds(inputIds);

        // assert deduplication happened: only 3 unique profiles returned
        assertEquals(3, result.size());

        // assert the returned ids match the inserted ids
        Set<Long> returnedIds = new HashSet<>();
        for (Profile profile : result) {
            returnedIds.add(profile.id);
        }
        Set<Long> expectedIds = new HashSet<>(inputIds);
        assertEquals(expectedIds, returnedIds);
    }

    @Test
    public void findProfilesByIds_returnsOnlyMatchingIds() {
        ProfileDao profileDao = mAppDatabase.profileDao();
        Profile profileA = insertProfile("profile A");
        Profile profileB = insertProfile("profile B");
        Profile profileC = insertProfile("profile C");

        // query 5 ids, 2 of which do not exist
        List<Long> inputIds = new ArrayList<>();
        inputIds.add(profileA.id);
        inputIds.add(999001L);
        inputIds.add(profileB.id);
        inputIds.add(profileC.id);
        inputIds.add(999002L);
        List<Profile> result = profileDao.findProfilesByIds(inputIds);

        // assert exactly the 3 existing profiles are returned
        assertEquals(3, result.size());
        Set<Long> returnedIds = new HashSet<>();
        for (Profile profile : result) {
            returnedIds.add(profile.id);
        }
        Set<Long> expectedIds = new HashSet<>();
        expectedIds.add(profileA.id);
        expectedIds.add(profileB.id);
        expectedIds.add(profileC.id);
        assertEquals(expectedIds, returnedIds);
    }

    @Test
    public void findProfilesByIds_returnsMatchesAcrossChunkBoundaries() {
        ProfileDao profileDao = mAppDatabase.profileDao();
        Profile profileA = insertProfile("profile A");
        Profile profileB = insertProfile("profile B");
        Profile profileC = insertProfile("profile C");

        // build a 1001-element id list with the real ids at positions 0, 500
        // and 1000 and non-existent filler elsewhere (this should chunk at
        // 500, then 500, then 1)
        List<Long> inputIds = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            if (i == 0) {
                inputIds.add(profileA.id);
            } else if (i == 500) {
                inputIds.add(profileB.id);
            } else if (i == 1000) {
                inputIds.add(profileC.id);
            } else {
                inputIds.add(1000000L + i);
            }
        }
        List<Profile> result = profileDao.findProfilesByIds(inputIds);

        // assert exactly the 3 existing profiles are returned
        assertEquals(3, result.size());
        Set<Long> returnedIds = new HashSet<>();
        for (Profile profile : result) {
            returnedIds.add(profile.id);
        }
        Set<Long> expectedIds = new HashSet<>();
        expectedIds.add(profileA.id);
        expectedIds.add(profileB.id);
        expectedIds.add(profileC.id);
        assertEquals(expectedIds, returnedIds);
    }
}
