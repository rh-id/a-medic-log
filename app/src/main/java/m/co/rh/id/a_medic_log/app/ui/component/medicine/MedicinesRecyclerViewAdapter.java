package m.co.rh.id.a_medic_log.app.ui.component.medicine;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.model.MedicineRow;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicinesCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class MedicinesRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedMedicinesCmd mMedicinesCmd;
    private MedicinesItemSV.MedicinesOnRowClick mOnRowClick;
    private MedicinesItemSV.MedicinesOnDeleteClick mOnDeleteClick;
    private MedicinesItemSV.MedicinesOnIntakeHistoryClick mOnIntakeHistoryClick;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public MedicinesRecyclerViewAdapter(PagedMedicinesCmd medicinesCmd,
                                              MedicinesItemSV.MedicinesOnRowClick onRowClick,
                                              MedicinesItemSV.MedicinesOnDeleteClick onDeleteClick,
                                              MedicinesItemSV.MedicinesOnIntakeHistoryClick onIntakeHistoryClick,
                                              INavigator navigator, StatefulView parentStatefulView
    ) {
        mMedicinesCmd = medicinesCmd;
        mOnRowClick = onRowClick;
        mOnDeleteClick = onDeleteClick;
        mOnIntakeHistoryClick = onIntakeHistoryClick;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = mNavigator.getActivity();
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = activity.getLayoutInflater().inflate(R.layout.no_medicine_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            MedicinesItemSV itemSV = new MedicinesItemSV();
            itemSV.setOnRowClick(mOnRowClick);
            itemSV.setOnDeleteClick(mOnDeleteClick);
            itemSV.setOnIntakeHistoryClick(mOnIntakeHistoryClick);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<MedicineRow> itemArrayList = mMedicinesCmd.getAllItems();
            MedicineRow item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mMedicinesCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mMedicinesCmd == null) {
            return true;
        }
        return mMedicinesCmd.getAllItems().size() == 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (StatefulView sv : mCreatedSvList) {
                sv.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private MedicinesItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, MedicinesItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(MedicineRow medicineRow) {
            mItemSV.setMedicineRow(medicineRow);
        }

        public MedicineRow getItem() {
            return mItemSV.getMedicineRow();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
