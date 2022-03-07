package m.co.rh.id.a_medic_log.base;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
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

@Database(entities = {Profile.class, Note.class, NoteTag.class, NoteAttachment.class,
        NoteAttachmentFile.class, Medicine.class, MedicineReminder.class, MedicineIntake.class
        , AndroidNotification.class},
        version = 3)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProfileDao profileDao();

    public abstract NoteDao noteDao();

    public abstract MedicineDao medicineDao();

    public abstract AndroidNotificationDao androidNotificationDao();
}
