package m.co.rh.id.a_medic_log.base.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.a_medic_log.base.state.ProfileState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class ProfileRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<ProfileDao> mProfileDao;
    private final ProviderValue<NoteDao> mNoteDao;
    private final ProviderValue<NoteRepository> mNoteRepository;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private final ProviderValue<NoteAttachmentDao> mNoteAttachmentDao;

    public ProfileRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mNoteRepository = provider.lazyGet(NoteRepository.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mNoteAttachmentDao = provider.lazyGet(NoteAttachmentDao.class);
    }

    /**
     * Inserts the profile, back-fills id/timestamps on the passed profile and
     * returns a state wrapping the persisted profile; note list is empty
     */
    public ProfileState insertProfile(Profile profile) {
        mAppDatabase.get().runInTransaction(() -> {
            if (profile.createdDateTime == null) {
                Date date = new Date();
                profile.createdDateTime = date;
                profile.updatedDateTime = date;
            }
            long id = mProfileDao.get().insert(profile);
            profile.id = id;
        });
        return new ProfileState(profile);
    }

    /**
     * Updates the profile and returns a state wrapping the persisted profile; note list is empty
     */
    public ProfileState updateProfile(Profile profile) {
        mAppDatabase.get().runInTransaction(() -> {
            profile.updatedDateTime = new Date();
            mProfileDao.get().update(profile);
        });
        return new ProfileState(profile);
    }

    public Profile findProfileById(long profileId) {
        return mProfileDao.get().findProfileById(profileId);
    }

    /**
     * Loads a fresh profile state with its notes and each note's nested data;
     * the returned state is never shared
     */
    public ProfileState queryProfileInfo(long profileId) {
        Profile profile = mProfileDao.get().findProfileById(profileId);
        ProfileState profileState = new ProfileState(profile);
        List<Note> notes = mNoteDao.get().findNotesByProfileId(profileId);
        ArrayList<NoteState> noteStates = new ArrayList<>();
        for (Note note : notes) {
            noteStates.add(mNoteRepository.get().queryNoteInfo(note.id));
        }
        profileState.updateNoteList(noteStates);
        return profileState;
    }

    /**
     * Counts the records owned by the given profile; direct DAO count
     * queries since loading the full profile state just to get the counts
     * would be expensive
     */
    public ProfileCounts queryProfileCounts(long profileId) {
        return new ProfileCounts(
                mNoteDao.get().countNotesByProfileId(profileId),
                mMedicineDao.get().countMedicinesByProfileId(profileId),
                mMedicineReminderDao.get().countMedicineRemindersByProfileId(profileId),
                mNoteAttachmentDao.get().countNoteAttachmentsByProfileId(profileId),
                mMedicineIntakeDao.get().countMedicineIntakesByProfileId(profileId));
    }

    /**
     * Counts of the records owned by a profile
     */
    public static class ProfileCounts {
        public final int noteCount;
        public final int medicineCount;
        public final int reminderCount;
        public final int attachmentCount;
        public final int intakeCount;

        public ProfileCounts(int noteCount, int medicineCount, int reminderCount,
                            int attachmentCount, int intakeCount) {
            this.noteCount = noteCount;
            this.medicineCount = medicineCount;
            this.reminderCount = reminderCount;
            this.attachmentCount = attachmentCount;
            this.intakeCount = intakeCount;
        }
    }
}
