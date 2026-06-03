package m.co.rh.id.a_medic_log.base.room.converter;

import androidx.room.ProvidedTypeConverter;
import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedHashSet;

import m.co.rh.id.alogger.ILogger;

@ProvidedTypeConverter
public class LinkedHashSetConverter {
    private static final String TAG = LinkedHashSetConverter.class.getName();
    private final ILogger mLogger;

    public LinkedHashSetConverter(ILogger logger) {
        mLogger = logger;
    }

    @TypeConverter
    public LinkedHashSet<Integer> linkedHashSetFromJsonString(String value) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (value == null || value.isEmpty()) {
            return result;
        }
        try {
            JSONArray jsonArray = new JSONArray(value);
            int size = jsonArray.length();
            for (int i = 0; i < size; i++) {
                result.add(jsonArray.getInt(i));
            }
        } catch (JSONException jsonException) {
            if (mLogger != null) {
                mLogger.e(TAG, "Failed to parse LinkedHashSet from JSON: " + value, jsonException);
            }
        }
        return result;
    }

    @TypeConverter
    public String linkedHashSetToJsonString(LinkedHashSet<Integer> integers) {
        JSONArray jsonArray = new JSONArray();
        if (integers != null) {
            for (Integer integer : integers) {
                jsonArray.put(integer);
            }
        }
        return jsonArray.toString();
    }

}
