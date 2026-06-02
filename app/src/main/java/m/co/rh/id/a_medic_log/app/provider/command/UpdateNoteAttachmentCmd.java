package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class UpdateNoteAttachmentCmd extends NewNoteAttachmentCmd {

    public UpdateNoteAttachmentCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromCallable(() -> {
            mNoteDao.update(noteAttachmentState.getNoteAttachment());
            return noteAttachmentState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
