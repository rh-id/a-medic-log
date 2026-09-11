package m.co.rh.id.a_medic_log.app.ui.component.report;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.AdherenceReport;

public class AdherenceReportAdapter extends RecyclerView.Adapter<AdherenceReportAdapter.ItemViewHolder> {
    private final List<AdherenceReport.MedicineAdherence> mItems;

    public AdherenceReportAdapter() {
        mItems = new ArrayList<>();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_adherence_medicine, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.setItem(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public void setItems(List<AdherenceReport.MedicineAdherence> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView mTextMedicineName;
        private final MaterialTextView mTextProfileName;
        private final MaterialTextView mTextAdherenceStats;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            mTextMedicineName = itemView.findViewById(R.id.text_medicine_name);
            mTextProfileName = itemView.findViewById(R.id.text_profile_name);
            mTextAdherenceStats = itemView.findViewById(R.id.text_adherence_stats);
        }

        public void setItem(AdherenceReport.MedicineAdherence item) {
            mTextMedicineName.setText(item.medicineName);
            mTextProfileName.setText(item.profileName);
            // "taken / missed / percent" e.g. "20 · 3 · 87%"
            mTextAdherenceStats.setText(String.format(Locale.getDefault(),
                    "%d · %d · %d%%", item.taken, item.missed, item.adherencePercent));
        }
    }
}
