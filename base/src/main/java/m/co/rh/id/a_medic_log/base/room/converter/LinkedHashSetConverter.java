package m.co.rh.id.a_medic_log.base.room.converter;

import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedHashSet;

public class LinkedHashSetConverter {
    @TypeConverter
    public static LinkedHashSet<Integer> linkedHashSetFromJsonString(String value) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        try {
            JSONArray jsonArray = new JSONArray(value);
            int size = jsonArray.length();
            for (int i = 0; i < size; i++) {
                result.add(jsonArray.getInt(i));
            }
        } catch (JSONException jsonException) {
            // leave blank
        }
        return result;
    }

    @TypeConverter
    public static String linkedHashSetToJsonString(LinkedHashSet<Integer> integers) {
        JSONArray jsonArray = new JSONArray();
        for (Integer integer : integers) {
            jsonArray.put(integer);
        }
        return jsonArray.toString();
    }

}
