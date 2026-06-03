package m.co.rh.id.a_medic_log.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAll() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4};
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

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_profile_id` ON `note` (`profile_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_entry_date_time` ON `note` (`entry_date_time`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_note_id` ON `note_tag` (`note_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_tag` ON `note_tag` (`tag`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_note_id` ON `note_attachment` (`note_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_file_attachment_id` ON `note_attachment_file` (`attachment_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_note_id` ON `medicine` (`note_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_reminder_medicine_id` ON `medicine_reminder` (`medicine_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_intake_medicine_id` ON `medicine_intake` (`medicine_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_android_notification_request_id` ON `android_notification` (`request_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_android_notification_group_key` ON `android_notification` (`group_key`)");
        }
    };
}
