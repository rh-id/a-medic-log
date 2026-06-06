package m.co.rh.id.a_medic_log.app.ui.component.settings;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;

public class LogLineRecyclerViewAdapter extends RecyclerView.Adapter<LogLineRecyclerViewAdapter.ItemViewHolder> {

    private final List<String> mLines;

    public LogLineRecyclerViewAdapter() {
        mLines = new ArrayList<>();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.mTextView.setText(mLines.get(position));
    }

    @Override
    public int getItemCount() {
        return mLines.size();
    }

    public void setLines(List<String> lines) {
        mLines.clear();
        mLines.addAll(lines);
        notifyDataSetChanged();
    }

    public void dispose(Activity activity) {
        mLines.clear();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        final TextView mTextView;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text_log_line);
        }
    }
}
