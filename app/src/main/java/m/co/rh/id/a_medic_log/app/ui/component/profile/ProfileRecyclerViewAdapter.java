package m.co.rh.id.a_medic_log.app.ui.component.profile;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.PagedProfileItemsCmd;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ProfileRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedProfileItemsCmd mPagedItemsCmd;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;
    private final ProfileItemSV.ListMode mListMode;

    public ProfileRecyclerViewAdapter(PagedProfileItemsCmd pagedItemsCmd,
                                      INavigator navigator, StatefulView parentStatefulView,
                                      ProfileItemSV.ListMode listMode) {
        mPagedItemsCmd = pagedItemsCmd;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
        mListMode = listMode;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = mNavigator.getActivity();
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = activity.getLayoutInflater().inflate(R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            ProfileItemSV itemSV = new ProfileItemSV(mListMode);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV, mPagedItemsCmd, mCreatedSvList);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<Profile> itemArrayList = mPagedItemsCmd.getAllItems();
            Profile item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Profile itemFromHolder = itemViewHolder.getItem();
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

    public void notifyItemAdded(Profile item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mPagedItemsCmd.getAllItems()
                    .add(0, item);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(Profile item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<Profile> items = mPagedItemsCmd.getAllItems();
            items.remove(existingIdx);
            items.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(Profile item) {
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

    private int findItem(Profile profile) {
        ArrayList<Profile> items =
                mPagedItemsCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (profile.id.equals(items.get(i).id)) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder implements ProfileItemSV.OnItemSelectListener {
        private ProfileItemSV mItemSV;
        private PagedProfileItemsCmd mPagedItemsCmd;
        private List<StatefulView> mCreatedDeckItemSvList;

        public ItemViewHolder(@NonNull View itemView, ProfileItemSV itemSV, PagedProfileItemsCmd pagedItemsCmd, List<StatefulView> createdDeckItemSvList) {
            super(itemView);
            mItemSV = itemSV;
            mPagedItemsCmd = pagedItemsCmd;
            mCreatedDeckItemSvList = createdDeckItemSvList;
            mItemSV.setOnSelectListener(this);
        }

        public void setItem(Profile profile) {
            mItemSV.setProfile(profile);
            ArrayList<Profile> selectedItems = mPagedItemsCmd.getSelectedItems();
            if (selectedItems.contains(profile)) {
                mItemSV.select();
            } else {
                mItemSV.unSelect();
            }
        }

        public Profile getItem() {
            return mItemSV.getProfile();
        }

        @Override
        public void onItemSelect(Profile profile, boolean selected) {
            if (selected) {
                mPagedItemsCmd.selectProfile(
                        profile);
                mItemSV.select();
            } else {
                mPagedItemsCmd.unSelectProfile(profile);
                mItemSV.unSelect();
            }
            for (StatefulView itemSv : mCreatedDeckItemSvList) {
                if (itemSv instanceof ProfileItemSV) {
                    ProfileItemSV profileItemSV = (ProfileItemSV) itemSv;
                    Profile profile1 = profileItemSV.getProfile();
                    if (profile1 != null && !profile1.id.equals(profile.id)) {
                        profileItemSV.unSelect();
                    }
                }

            }
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
