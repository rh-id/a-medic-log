package m.co.rh.id.a_medic_log.base;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.AndroidNotification;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;

@Database(entities = {Profile.class, Note.class, NoteTag.class, NoteAttachment.class,
        NoteAttachmentFile.class, Medicine.class, MedicineReminder.class, MedicineIntake.class
        , AndroidNotification.class},
        version = 5)
@TypeConverters({LinkedHashSetConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProfileDao profileDao();

    public abstract NoteDao noteDao();

    public abstract NoteAttachmentDao noteAttachmentDao();

    public abstract NoteTagDao noteTagDao();

    public abstract NoteAttachmentFileDao noteAttachmentFileDao();

    public abstract MedicineReminderDao medicineReminderDao();

    public abstract MedicineIntakeDao medicineIntakeDao();

    public abstract MedicineDao medicineDao();

    public abstract AndroidNotificationDao androidNotificationDao();
}
