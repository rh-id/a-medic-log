package m.co.rh.id.a_medic_log.app.ui.component.note.attachment;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class NoteAttachmentRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private NoteState mNoteState;
    NoteAttachmentItemSV.NoteAttachmentItemOnEditClick mNoteAttachmentItemOnEditClick;
    NoteAttachmentItemSV.NoteAttachmentItemOnDeleteClick mNoteAttachmentItemOnDeleteClick;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public NoteAttachmentRecyclerViewAdapter(NoteState noteState,
                                             NoteAttachmentItemSV.NoteAttachmentItemOnEditClick noteAttachmentItemOnEditClick,
                                             NoteAttachmentItemSV.NoteAttachmentItemOnDeleteClick noteAttachmentItemOnDeleteClick,
                                             INavigator navigator, StatefulView parentStatefulView
    ) {
        mNoteState = noteState;
        mNoteAttachmentItemOnEditClick = noteAttachmentItemOnEditClick;
        mNoteAttachmentItemOnDeleteClick = noteAttachmentItemOnDeleteClick;
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
            NoteAttachmentItemSV itemSV = new NoteAttachmentItemSV();
            itemSV.setNoteAttachmentItemOnEditClick(mNoteAttachmentItemOnEditClick);
            itemSV.setNoteAttachmentItemOnDeleteClick(mNoteAttachmentItemOnDeleteClick);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<NoteAttachmentState> itemArrayList = mNoteState.getNoteAttachmentStates();
            NoteAttachmentState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mNoteState.getNoteAttachmentStates().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mNoteState == null || mNoteState.getNoteAttachmentStates() == null) {
            return true;
        }
        return mNoteState.getNoteAttachmentStates().size() == 0;
    }

    public void notifyItemAdded(NoteAttachmentState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mNoteState.getNoteAttachmentStates()
                    .add(0, item);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(NoteAttachmentState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<NoteAttachmentState> items = mNoteState.getNoteAttachmentStates();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(NoteAttachmentState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mNoteState.getNoteAttachmentStates()
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

    private int findItem(NoteAttachmentState item) {
        ArrayList<NoteAttachmentState> items =
                mNoteState.getNoteAttachmentStates();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getNoteAttachmentCreatedDateTime().equals(items.get(i).getNoteAttachmentCreatedDateTime())) {
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
        private NoteAttachmentItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, NoteAttachmentItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(NoteAttachmentState noteAttachmentState) {
            mItemSV.setNoteAttachmentState(noteAttachmentState);
        }

        public NoteAttachmentState getItem() {
            return mItemSV.getNoteAttachmentState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
