package m.co.rh.id.a_medic_log.base;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@RunWith(AndroidJUnit4.class)
public class DbMigrationTest {
    private static final String TEST_DB = DbMigrationTest.class.getName()
            + "-migration-test";

    @Rule
    public MigrationTestHelper helper;

    @SuppressWarnings("deprecation")
    public DbMigrationTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrateAll() throws IOException {
        // Create earliest version of the database.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.close();

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        AppDatabase appDb = Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .addMigrations(DbMigration.getAll())
                .addTypeConverter(new LinkedHashSetConverter(null))
                .build();
        appDb.getOpenHelper().getWritableDatabase();
        appDb.close();
    }

    @Test
    public void migrate5To6() throws IOException {
        // Create version 5 of the database with a medicine and a reminder row.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        db.execSQL("INSERT INTO `medicine` (`note_id`, `name`, `description`, " +
                "`created_date_time`, `updated_date_time`) " +
                "VALUES (NULL, 'test medicine', NULL, 1000, 1000)");
        db.execSQL("INSERT INTO `medicine_reminder` (`medicine_id`, `start_date_time`, " +
                "`reminder_enabled`, `message`, `reminder_days`, " +
                "`created_date_time`, `updated_date_time`) " +
                "VALUES (1, 1000, 1, 'test message', '[]', 2000, 2000)");
        db.close();

        // Run the 5 to 6 migration, Room validates the resulting schema.
        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true,
                DbMigration.MIGRATION_5_6);
        // pre-existing reminders are treated as if they were enabled at creation
        Cursor cursor = migrated.query("SELECT `last_enabled_date_time`, `last_disabled_date_time` " +
                "FROM `medicine_reminder` WHERE `id` = 1");
        assertTrue(cursor.moveToFirst());
        assertEquals(2000, cursor.getLong(0));
        assertTrue(cursor.isNull(1));
        cursor.close();
        // the unique composite index makes occurrence generation idempotent
        migrated.execSQL("INSERT INTO `medicine_dose_occurrence` (`reminder_id`, `medicine_id`, " +
                "`scheduled_date_time`, `created_date_time`) VALUES (1, 1, 5000, 5000)");
        try {
            migrated.execSQL("INSERT INTO `medicine_dose_occurrence` (`reminder_id`, `medicine_id`, " +
                    "`scheduled_date_time`, `created_date_time`) VALUES (1, 1, 5000, 5000)");
            fail("expected unique constraint violation on (reminder_id, scheduled_date_time)");
        } catch (SQLiteConstraintException e) {
            // expected, the same expected dose cannot be recorded twice
        }
        migrated.close();
    }
}
