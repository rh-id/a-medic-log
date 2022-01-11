package m.co.rh.id.a_medic_log.app.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.entity.Profile;

public class ProfileChangeNotifier {
    private PublishSubject<Profile> mAddedProfileSubject;
    private PublishSubject<Profile> mUpdatedProfileSubject;
    private PublishSubject<Profile> mDeletedProfileSubject;

    public ProfileChangeNotifier() {
        mAddedProfileSubject = PublishSubject.create();
        mUpdatedProfileSubject = PublishSubject.create();
        mDeletedProfileSubject = PublishSubject.create();
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
