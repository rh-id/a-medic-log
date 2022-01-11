package m.co.rh.id.a_medic_log.app.ui.component.medicine;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class MedicineRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private NoteState mNoteState;
    private MedicineItemSV.OnMedicineIntakeListClick mOnMedicineIntakeListClick;
    private MedicineItemSV.OnEditClick mOnEditClick;
    private MedicineItemSV.OnDeleteClick mOnDeleteClick;
    private MedicineItemSV.OnAddMedicineIntakeClick mOnAddMedicineIntakeClick;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public MedicineRecyclerViewAdapter(NoteState noteState,
                                       MedicineItemSV.OnMedicineIntakeListClick onMedicineIntakeListClick,
                                       MedicineItemSV.OnEditClick onEditClick,
                                       MedicineItemSV.OnDeleteClick onDeleteClickClick,
                                       MedicineItemSV.OnAddMedicineIntakeClick onAddMedicineIntakeClick,
                                       INavigator navigator, StatefulView parentStatefulView
    ) {
        mNoteState = noteState;
        mOnMedicineIntakeListClick = onMedicineIntakeListClick;
        mOnEditClick = onEditClick;
        mOnDeleteClick = onDeleteClickClick;
        mOnAddMedicineIntakeClick = onAddMedicineIntakeClick;
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
            MedicineItemSV itemSV = new MedicineItemSV();
            itemSV.setOnMedicineIntakeListClick(mOnMedicineIntakeListClick);
            itemSV.setOnEditClick(mOnEditClick);
            itemSV.setOnDeleteClick(mOnDeleteClick);
            itemSV.setOnAddMedicineIntakeClick(mOnAddMedicineIntakeClick);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<MedicineState> itemArrayList = mNoteState.getMedicineList();
            MedicineState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mNoteState.getMedicineList().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mNoteState == null) {
            return true;
        }
        return mNoteState.getMedicineList().size() == 0;
    }

    public void notifyItemAdded(MedicineState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mNoteState.addMedicineList(item);
            ArrayList<MedicineState> items = mNoteState.getMedicineList();
            if (items.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(getItemCount() - 1);
            }
        }
    }

    public void notifyItemUpdated(MedicineState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            mNoteState.updateMedicineList(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(MedicineState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mNoteState.removeMedicineList(removedIdx);
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

    private int findItem(MedicineState item) {
        ArrayList<MedicineState> items =
                mNoteState.getMedicineList();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getMedicine().createdDateTime.equals(
                    items.get(i).getMedicine().createdDateTime)) {
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
        private MedicineItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, MedicineItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(MedicineState medicineState) {
            mItemSV.setMedicineState(medicineState);
        }

        public MedicineState getItem() {
            return mItemSV.getMedicineState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
