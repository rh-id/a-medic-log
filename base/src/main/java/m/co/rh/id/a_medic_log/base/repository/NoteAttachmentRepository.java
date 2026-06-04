package m.co.rh.id.a_medic_log.base.repository;

import java.util.Collection;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NoteAttachmentRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<NoteAttachmentDao> mNoteAttachmentDao;
    private final ProviderValue<NoteAttachmentFileDao> mNoteAttachmentFileDao;

    public NoteAttachmentRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mNoteAttachmentDao = provider.lazyGet(NoteAttachmentDao.class);
        mNoteAttachmentFileDao = provider.lazyGet(NoteAttachmentFileDao.class);
    }

    public void insertNoteAttachment(NoteAttachmentState noteAttachmentState) {
        mAppDatabase.get().runInTransaction(() -> {
            NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
            long noteAttachmentId = mNoteAttachmentDao.get().insert(noteAttachment);
            noteAttachment.id = noteAttachmentId;
            Collection<NoteAttachmentFile> noteAttachmentFiles =
                    noteAttachmentState.getNoteAttachmentFiles();
            if (noteAttachmentFiles != null && !noteAttachmentFiles.isEmpty()) {
                for (NoteAttachmentFile noteAttachmentFile : noteAttachmentFiles) {
                    noteAttachmentFile.attachmentId = noteAttachmentId;
                    mNoteAttachmentFileDao.get().insert(noteAttachmentFile);
                }
            }
        });
    }

    public void deleteNoteAttachment(NoteAttachmentState noteAttachmentState) {
        mAppDatabase.get().runInTransaction(() -> {
            NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
            Long noteAttachmentId = noteAttachment.id;
            if (noteAttachmentId != null) {
                mNoteAttachmentFileDao.get().deleteNoteAttachmentFilesByAttachmentId(noteAttachmentId);
                mNoteAttachmentDao.get().delete(noteAttachment);
            }
        });
    }
}