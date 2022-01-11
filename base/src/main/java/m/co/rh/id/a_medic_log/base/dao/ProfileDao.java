package m.co.rh.id.a_medic_log.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.entity.Profile;

@Dao
public abstract class ProfileDao {

    @Query("SELECT * FROM profile ORDER BY created_date_time DESC LIMIT :limit")
    public abstract List<Profile> loadProfilesWithLimit(int limit);

    @Query("SELECT * FROM profile WHERE name LIKE '%'||:search||'%' OR " +
            "about LIKE '%'||:search||'%' ORDER BY name ASC")
    public abstract List<Profile> searchProfile(String search);

    @Query("SELECT * FROM profile WHERE id = :id")
    public abstract Profile findProfileById(long id);

    @Transaction
    public void insertProfile(Profile profile) {
        if (profile.createdDateTime == null) {
            Date date = new Date();
            profile.createdDateTime = date;
            profile.updatedDateTime = date;
        }
        profile.id = insert(profile);
    }

    @Transaction
    public void updateProfile(Profile profile) {
        profile.updatedDateTime = new Date();
        update(profile);
    }

    @Insert
    protected abstract long insert(Profile profile);

    @Update
    protected abstract void update(Profile profile);

    @Delete
    public abstract void delete(Profile profile);
}
