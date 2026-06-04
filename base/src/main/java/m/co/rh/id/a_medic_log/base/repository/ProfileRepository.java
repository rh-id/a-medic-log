package m.co.rh.id.a_medic_log.base.repository;

import java.util.Date;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class ProfileRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<ProfileDao> mProfileDao;

    public ProfileRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
    }

    public void insertProfile(Profile profile) {
        mAppDatabase.get().runInTransaction(() -> {
            if (profile.createdDateTime == null) {
                Date date = new Date();
                profile.createdDateTime = date;
                profile.updatedDateTime = date;
            }
            long id = mProfileDao.get().insert(profile);
            profile.id = id;
        });
    }

    public void updateProfile(Profile profile) {
        mAppDatabase.get().runInTransaction(() -> {
            profile.updatedDateTime = new Date();
            mProfileDao.get().update(profile);
        });
    }
}