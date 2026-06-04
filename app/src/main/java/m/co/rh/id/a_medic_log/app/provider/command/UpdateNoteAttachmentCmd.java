package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class UpdateNoteAttachmentCmd extends NewNoteAttachmentCmd {
    protected NoteAttachmentDao mNoteAttachmentDao;

    public UpdateNoteAttachmentCmd(Provider provider) {
        super(provider);
        mNoteAttachmentDao = provider.get(NoteAttachmentDao.class);
    }

    @Override
    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromCallable(() -> {
            mNoteAttachmentDao.update(noteAttachmentState.getNoteAttachment());
            return noteAttachmentState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
