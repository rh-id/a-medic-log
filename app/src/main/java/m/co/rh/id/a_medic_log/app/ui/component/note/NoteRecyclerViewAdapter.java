package m.co.rh.id.a_medic_log.app.ui.component.note;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.PagedNoteItemsCmd;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class NoteRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedNoteItemsCmd mPagedItemsCmd;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public NoteRecyclerViewAdapter(PagedNoteItemsCmd pagedItemsCmd,
                                   INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemsCmd = pagedItemsCmd;
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
            NoteItemSV itemSV = new NoteItemSV();
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<Note> itemArrayList = mPagedItemsCmd.getAllItems();
            Note item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Note itemFromHolder = itemViewHolder.getItem();
            if (itemFromHolder == null || !itemFromHolder.equals(item)) {
                itemViewHolder.setItem(item);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemsCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemsCmd == null) {
            return true;
        }
        return mPagedItemsCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(Note item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mPagedItemsCmd.getAllItems()
                    .add(0, item);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(Note item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<Note> items = mPagedItemsCmd.getAllItems();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(Note item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mPagedItemsCmd.getAllItems()
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

    private int findItem(Note item) {
        ArrayList<Note> items =
                mPagedItemsCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.id.equals(items.get(i).id)) {
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
        private NoteItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, NoteItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(Note note) {
            mItemSV.setNote(note);
        }

        public Note getItem() {
            return mItemSV.getNote();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
