package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.entity.Profile;

public class ProfileChangeNotifier {
    private Subject<Profile> mAddedProfileSubject;
    private Subject<Profile> mUpdatedProfileSubject;
    private Subject<Profile> mDeletedProfileSubject;

    public ProfileChangeNotifier() {
        mAddedProfileSubject = PublishSubject.<Profile>create().toSerialized();
        mUpdatedProfileSubject = PublishSubject.<Profile>create().toSerialized();
        mDeletedProfileSubject = PublishSubject.<Profile>create().toSerialized();
    }

    public void profileAdded(Profile profile) {
        mAddedProfileSubject.onNext(profile);
    }

    public void profileUpdated(Profile profile) {
        mUpdatedProfileSubject.onNext(profile);
    }

    public void profileDeleted(Profile profile) {
        mDeletedProfileSubject.onNext(profile);
    }

    public Flowable<Profile> getAddedProfile() {
        return Flowable.fromObservable(mAddedProfileSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Profile> getUpdatedProfile() {
        return Flowable.fromObservable(mUpdatedProfileSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Profile> getDeletedProfile() {
        return Flowable.fromObservable(mDeletedProfileSubject, BackpressureStrategy.BUFFER);
    }
}
