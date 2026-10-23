package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.state.ProfileState;

public class ProfileChangeNotifier {
    private Subject<ProfileState> mAddedProfileSubject;
    private Subject<ProfileState> mUpdatedProfileSubject;
    private Subject<ProfileState> mDeletedProfileSubject;

    public ProfileChangeNotifier() {
        mAddedProfileSubject = PublishSubject.<ProfileState>create().toSerialized();
        mUpdatedProfileSubject = PublishSubject.<ProfileState>create().toSerialized();
        mDeletedProfileSubject = PublishSubject.<ProfileState>create().toSerialized();
    }

    public void profileAdded(ProfileState profileState) {
        mAddedProfileSubject.onNext(profileState);
    }

    public void profileUpdated(ProfileState profileState) {
        mUpdatedProfileSubject.onNext(profileState);
    }

    public void profileDeleted(ProfileState profileState) {
        mDeletedProfileSubject.onNext(profileState);
    }

    public Flowable<ProfileState> getAddedProfile() {
        return Flowable.fromObservable(mAddedProfileSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ProfileState> getUpdatedProfile() {
        return Flowable.fromObservable(mUpdatedProfileSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ProfileState> getDeletedProfile() {
        return Flowable.fromObservable(mDeletedProfileSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Merges the added, updated and deleted streams into a single change stream.
     */
    public Flowable<ProfileState> getAnyChanged() {
        return Flowable.merge(getAddedProfile(), getUpdatedProfile(), getDeletedProfile());
    }
}
