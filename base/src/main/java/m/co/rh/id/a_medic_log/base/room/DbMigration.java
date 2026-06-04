package m.co.rh.id.a_medic_log.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAll() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5};
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

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `note_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`profile_id` INTEGER, " +
                    "`entry_date_time` INTEGER, " +
                    "`content` TEXT, " +
                    "`created_date_time` INTEGER, " +
                    "`updated_date_time` INTEGER, " +
                    "FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `note_new` SELECT * FROM `note`");
            database.execSQL("DROP TABLE `note`");
            database.execSQL("ALTER TABLE `note_new` RENAME TO `note`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_profile_id` ON `note`(`profile_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_entry_date_time` ON `note`(`entry_date_time`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `note_tag_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`note_id` INTEGER, " +
                    "`tag` TEXT, " +
                    "FOREIGN KEY(`note_id`) REFERENCES `note`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `note_tag_new` SELECT * FROM `note_tag`");
            database.execSQL("DROP TABLE `note_tag`");
            database.execSQL("ALTER TABLE `note_tag_new` RENAME TO `note_tag`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_note_id` ON `note_tag`(`note_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_tag` ON `note_tag`(`tag`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `note_attachment_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`note_id` INTEGER, " +
                    "`name` TEXT, " +
                    "`created_date_time` INTEGER, " +
                    "FOREIGN KEY(`note_id`) REFERENCES `note`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `note_attachment_new` SELECT * FROM `note_attachment`");
            database.execSQL("DROP TABLE `note_attachment`");
            database.execSQL("ALTER TABLE `note_attachment_new` RENAME TO `note_attachment`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_note_id` ON `note_attachment`(`note_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `note_attachment_file_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`attachment_id` INTEGER, " +
                    "`file_name` TEXT, " +
                    "`created_date_time` INTEGER, " +
                    "FOREIGN KEY(`attachment_id`) REFERENCES `note_attachment`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `note_attachment_file_new` SELECT * FROM `note_attachment_file`");
            database.execSQL("DROP TABLE `note_attachment_file`");
            database.execSQL("ALTER TABLE `note_attachment_file_new` RENAME TO `note_attachment_file`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_file_attachment_id` ON `note_attachment_file`(`attachment_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `medicine_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`note_id` INTEGER, " +
                    "`name` TEXT, " +
                    "`description` TEXT, " +
                    "`created_date_time` INTEGER, " +
                    "`updated_date_time` INTEGER, " +
                    "FOREIGN KEY(`note_id`) REFERENCES `note`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `medicine_new` SELECT * FROM `medicine`");
            database.execSQL("DROP TABLE `medicine`");
            database.execSQL("ALTER TABLE `medicine_new` RENAME TO `medicine`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_note_id` ON `medicine`(`note_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `medicine_reminder_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`medicine_id` INTEGER, " +
                    "`start_date_time` INTEGER, " +
                    "`reminder_enabled` INTEGER, " +
                    "`message` TEXT, " +
                    "`reminder_days` TEXT, " +
                    "`created_date_time` INTEGER, " +
                    "`updated_date_time` INTEGER, " +
                    "FOREIGN KEY(`medicine_id`) REFERENCES `medicine`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `medicine_reminder_new` SELECT * FROM `medicine_reminder`");
            database.execSQL("DROP TABLE `medicine_reminder`");
            database.execSQL("ALTER TABLE `medicine_reminder_new` RENAME TO `medicine_reminder`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_reminder_medicine_id` ON `medicine_reminder`(`medicine_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `medicine_intake_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicine_id` INTEGER, " +
                    "`description` TEXT, " +
                    "`taken_date_time` INTEGER, " +
                    "`created_date_time` INTEGER, " +
                    "`updated_date_time` INTEGER, " +
                    "FOREIGN KEY(`medicine_id`) REFERENCES `medicine`(`id`) ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `medicine_intake_new` SELECT * FROM `medicine_intake`");
            database.execSQL("DROP TABLE `medicine_intake`");
            database.execSQL("ALTER TABLE `medicine_intake_new` RENAME TO `medicine_intake`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_intake_medicine_id` ON `medicine_intake`(`medicine_id`)");
        }
    };
}
