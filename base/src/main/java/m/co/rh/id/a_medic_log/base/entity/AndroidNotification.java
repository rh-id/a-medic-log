package m.co.rh.id.a_medic_log.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "android_notification", indices = {
        @Index(value = "request_id"),
        @Index(value = "group_key")
})
public class AndroidNotification implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "request_id")
    public int requestId;

    @ColumnInfo(name = "group_key")
    public String groupKey;

    // ref id can refer to table id
    @ColumnInfo(name = "ref_id")
    public long refId;
}
