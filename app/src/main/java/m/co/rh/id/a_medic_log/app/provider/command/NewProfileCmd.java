package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewProfileCmd {
    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ProfileDao> mProfileDao;
    protected ProviderValue<ProfileChangeNotifier> mProfileChangeNotifier;
    protected BehaviorSubject<String> mNameValidSubject;

    public NewProfileCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mProfileChangeNotifier = provider.lazyGet(ProfileChangeNotifier.class);
        mNameValidSubject = BehaviorSubject.create();
    }

    public Single<Profile> execute(Profile profile) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mProfileDao.get().insertProfile(profile);
            mProfileChangeNotifier.get().profileAdded(profile);
            return profile;
        }));
    }

    public boolean valid(Profile profile) {
        boolean isValid = false;
        if (profile != null) {
            boolean nameValid = false;
            if (profile.name != null && !profile.name.isEmpty()) {
                nameValid = true;
                mNameValidSubject.onNext("");
            } else {
                mNameValidSubject.onNext(mAppContext.getString(R.string.name_is_required));
            }
            isValid = nameValid;
        }
        return isValid;
    }

    public Flowable<String> getNameValid() {
        return Flowable.fromObservable(mNameValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String nameValid = mNameValidSubject.getValue();
        if (nameValid != null && !nameValid.isEmpty()) {
            return nameValid;
        }
        return "";
    }
}
