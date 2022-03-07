package m.co.rh.id.a_medic_log.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAll() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " +
                    "`note_tag` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`note_id` INTEGER, " +
                    "`tag` TEXT)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " +
                    "`note_attachment` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`note_id` INTEGER, " +
                    "`name` TEXT, " +
                    "`created_date_time` INTEGER)");
            database.execSQL("CREATE TABLE IF NOT EXISTS " +
                    "`note_attachment_file` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`attachment_id` INTEGER, " +
                    "`file_name` TEXT, " +
                    "`created_date_time` INTEGER)");
        }
    };
}
