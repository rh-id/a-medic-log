package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;

public class UpdateNoteCmd extends NewNoteCmd {

    public UpdateNoteCmd(Provider provider) {
        super(provider);
    }

    public Single<NoteState> execute(NoteState noteState) {
        return Single.fromCallable(() -> {
            NoteState beforeUpdate = mNoteRepository.get().queryNoteInfo(noteState.getNoteId());
            mNoteRepository.get().updateNote(noteState);
            mNoteChangeNotifier.get().noteUpdated(beforeUpdate, noteState.clone());
            return noteState;
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
