package m.co.rh.id.a_medic_log.app.ui.component.note.attachment;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class NoteAttachmentFileRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private NoteAttachmentState mNoteAttachmentState;
    private NoteAttachmentFileItemSV.NoteAttachmentFileItemOnDeleteClick mNoteAttachmentFileItemOnDeleteClick;
    private boolean mDisplayOnly;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public NoteAttachmentFileRecyclerViewAdapter(NoteAttachmentState noteAttachmentState,
                                                 NoteAttachmentFileItemSV.NoteAttachmentFileItemOnDeleteClick noteAttachmentFileItemOnDeleteClick,
                                                 INavigator navigator, StatefulView parentStatefulView
    ) {
        this(noteAttachmentState, noteAttachmentFileItemOnDeleteClick, false, navigator, parentStatefulView);
    }

    public NoteAttachmentFileRecyclerViewAdapter(NoteAttachmentState noteAttachmentState,
                                                 NoteAttachmentFileItemSV.NoteAttachmentFileItemOnDeleteClick noteAttachmentFileItemOnDeleteClick,
                                                 boolean displayOnly,
                                                 INavigator navigator, StatefulView parentStatefulView
    ) {
        mNoteAttachmentState = noteAttachmentState;
        mNoteAttachmentFileItemOnDeleteClick = noteAttachmentFileItemOnDeleteClick;
        mDisplayOnly = displayOnly;
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
            NoteAttachmentFileItemSV itemSV = new NoteAttachmentFileItemSV(mDisplayOnly);
            itemSV.setNoteAttachmentFileItemOnDeleteClick(mNoteAttachmentFileItemOnDeleteClick);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            FrameLayout frameLayout = new FrameLayout(activity);
            if (mDisplayOnly) {
                frameLayout.setLayoutParams(new ViewGroup.LayoutParams(500, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            View view = itemSV.buildView(activity, frameLayout);
            frameLayout.addView(view);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(frameLayout, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<NoteAttachmentFile> itemArrayList = mNoteAttachmentState.getNoteAttachmentFiles();
            NoteAttachmentFile item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mNoteAttachmentState.getNoteAttachmentFiles().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mNoteAttachmentState == null || mNoteAttachmentState.getNoteAttachmentFiles() == null) {
            return true;
        }
        return mNoteAttachmentState.getNoteAttachmentFiles().size() == 0;
    }

    public void notifyItemAdded(NoteAttachmentFile item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mNoteAttachmentState.getNoteAttachmentFiles()
                    .add(0, item);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(NoteAttachmentFile item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<NoteAttachmentFile> items = mNoteAttachmentState.getNoteAttachmentFiles();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(NoteAttachmentFile item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mNoteAttachmentState.getNoteAttachmentFiles()
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

    private int findItem(NoteAttachmentFile item) {
        ArrayList<NoteAttachmentFile> items =
                mNoteAttachmentState.getNoteAttachmentFiles();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.createdDateTime.equals(items.get(i).createdDateTime)) {
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
        private NoteAttachmentFileItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, NoteAttachmentFileItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(NoteAttachmentFile noteAttachmentFile) {
            mItemSV.setNoteAttachmentFile(noteAttachmentFile);
        }

        public NoteAttachmentFile getItem() {
            return mItemSV.getNoteAttachmentFile();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
