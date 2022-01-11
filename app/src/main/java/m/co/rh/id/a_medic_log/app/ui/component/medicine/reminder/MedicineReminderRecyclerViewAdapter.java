package m.co.rh.id.a_medic_log.app.ui.component.medicine.reminder;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class MedicineReminderRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private MedicineState mMedicineState;
    private MedicineReminderItemSV.OnEnableSwitchClick mOnEnableSwitchClick;
    private MedicineReminderItemSV.OnEditClick mOnEditClick;
    private MedicineReminderItemSV.OnDeleteClick mOnDeleteClick;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public MedicineReminderRecyclerViewAdapter(MedicineState medicineState,
                                               MedicineReminderItemSV.OnEnableSwitchClick onEnableSwitchClick,
                                               MedicineReminderItemSV.OnEditClick onEditClick,
                                               MedicineReminderItemSV.OnDeleteClick onDeleteClickClick,
                                               INavigator navigator, StatefulView parentStatefulView
    ) {
        mMedicineState = medicineState;
        mOnEnableSwitchClick = onEnableSwitchClick;
        mOnEditClick = onEditClick;
        mOnDeleteClick = onDeleteClickClick;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = mNavigator.getActivity();
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = activity.getLayoutInflater().inflate(R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            MedicineReminderItemSV itemSV = new MedicineReminderItemSV();
            itemSV.setOnEnableSwitchClick(mOnEnableSwitchClick);
            itemSV.setOnEditClick(mOnEditClick);
            itemSV.setOnDeleteClick(mOnDeleteClick);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<MedicineReminder> itemArrayList = mMedicineState.getMedicineReminderList();
            MedicineReminder item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mMedicineState.getMedicineReminderList().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mMedicineState == null) {
            return true;
        }
        return mMedicineState.getMedicineReminderList().size() == 0;
    }

    public void notifyItemAdded(MedicineReminder item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<MedicineReminder> items = mMedicineState.getMedicineReminderList();
            items.add(item);
            if (items.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(getItemCount() - 1);
            }
        }
    }

    public void notifyItemUpdated(MedicineReminder item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<MedicineReminder> items = mMedicineState.getMedicineReminderList();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(MedicineReminder item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mMedicineState.getMedicineReminderList()
                    .remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
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

    private int findItem(MedicineReminder item) {
        ArrayList<MedicineReminder> items =
                mMedicineState.getMedicineReminderList();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.createdDateTime.equals(
                    items.get(i).createdDateTime)) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private MedicineReminderItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, MedicineReminderItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(MedicineReminder medicineReminder) {
            mItemSV.setMedicineReminder(medicineReminder);
        }

        public MedicineReminder getItem() {
            return mItemSV.getMedicineReminder();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
