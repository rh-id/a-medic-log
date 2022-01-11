package m.co.rh.id.a_medic_log.app.ui.component.medicine.intake;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicineIntakeItemsCmd;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class MedicineIntakeRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedMedicineIntakeItemsCmd mPagedMedicineIntakeItemsCmd;
    private MedicineIntakeItemSV.OnEditClick mOnEditClick;
    private MedicineIntakeItemSV.OnDeleteClick mOnDeleteClick;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public MedicineIntakeRecyclerViewAdapter(PagedMedicineIntakeItemsCmd pagedMedicineIntakeItemsCmd,
                                             MedicineIntakeItemSV.OnEditClick onEditClick,
                                             MedicineIntakeItemSV.OnDeleteClick onDeleteClickClick,
                                             INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedMedicineIntakeItemsCmd = pagedMedicineIntakeItemsCmd;
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
            MedicineIntakeItemSV itemSV = new MedicineIntakeItemSV();
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
            ArrayList<MedicineIntake> itemArrayList = mPagedMedicineIntakeItemsCmd.getAllItems();
            MedicineIntake item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedMedicineIntakeItemsCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedMedicineIntakeItemsCmd == null) {
            return true;
        }
        return mPagedMedicineIntakeItemsCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(MedicineIntake item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<MedicineIntake> items = mPagedMedicineIntakeItemsCmd.getAllItems();
            items.add(item);
            if (items.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(getItemCount() - 1);
            }
        }
    }

    public void notifyItemUpdated(MedicineIntake item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<MedicineIntake> items = mPagedMedicineIntakeItemsCmd.getAllItems();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(MedicineIntake item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mPagedMedicineIntakeItemsCmd.getAllItems()
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

    private int findItem(MedicineIntake item) {
        ArrayList<MedicineIntake> items =
                mPagedMedicineIntakeItemsCmd.getAllItems();
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
        private MedicineIntakeItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, MedicineIntakeItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(MedicineIntake medicineIntake) {
            mItemSV.setMedicineIntake(medicineIntake);
        }

        public MedicineIntake getItem() {
            return mItemSV.getMedicineIntake();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
