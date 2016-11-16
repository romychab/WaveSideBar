package com.gjiazhe.wavesidebar;

import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class WaveSideBarConnector
        implements
            WaveSideBar.OnSelectIndexItemListener,
            AbsListView.OnScrollListener {

    private ListAdapter mAdapter;
    private WaveSideBar mCustomSidebar;
    private ListView mListView;
    private int mLastIndex = -1;
    private boolean mRestrictScrollHandling = false;

    private Map<String, Integer> mPos = new HashMap<>();

    private Map<String, Integer> mSidebarIndexes = new HashMap<>();

    /**
     * Connect Sidebar and ListView
     *
     * Alphabet will be generated using first letters from toString() of ListView items.
     * Items have to be sorted!
     */
    public WaveSideBarConnector(ListView lv, WaveSideBar sidebar) {
        mAdapter = lv.getAdapter();
        mCustomSidebar = sidebar;
        mListView = lv;
        update();
    }

    public void update() {
        mPos.clear();
        mSidebarIndexes.clear();
        mCustomSidebar.setIndexItems(gatherLetters());
        mCustomSidebar.setOnSelectIndexItemListener(this);
        mListView.setOnScrollListener(this);
    }

    private String[] gatherLetters() {
        Set<String> letters = new LinkedHashSet<>();
        int prevSize;
        String key;
        for (int i = 0; i < mAdapter.getCount(); i++) {
            try {
                prevSize = letters.size();
                key = mAdapter.getItem(i).toString().toLowerCase().substring(0, 1);
                letters.add(key);
                mSidebarIndexes.put(mAdapter.getItem(i).toString(), letters.size() - 1);
                if (letters.size() > prevSize) {
                    mPos.put(key, i);
                }
            } catch (Exception e) { ; }
        }
        return letters.toArray(new String[letters.size()]);
    }

    // --- CustomSidebar.OnSelectIndexItemListener impl.

    @Override
    public void onSelectIndexItem(String index) {
        mRestrictScrollHandling = true;
        mListView.setSelection(mPos.get(index));
    }

    // --- OnScrollListener impl.


    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mRestrictScrollHandling) {
            mRestrictScrollHandling = false;
            return;
        }

        if (mLastIndex != firstVisibleItem) {
            Integer index = mSidebarIndexes.get(mAdapter.getItem(firstVisibleItem).toString());
            if (null != index) {
                mLastIndex = index;
                mCustomSidebar.setCurrentIndex(index);
            }
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }
}
