/*
 * This file is part of Butter.
 *
 * Butter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Butter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Butter. If not, see <http://www.gnu.org/licenses/>.
 */

package pirateflix.droid.tv.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import pirateflix.droid.base.manager.provider.ProviderManager;
import pirateflix.droid.base.providers.media.MediaProvider;
import pirateflix.droid.base.providers.media.models.Media;
import pirateflix.droid.tv.R;
import pirateflix.droid.tv.TVButterApplication;
import pirateflix.droid.tv.activities.TVMediaDetailActivity;
import pirateflix.droid.tv.presenters.MediaCardPresenter;
import pirateflix.droid.tv.utils.BackgroundUpdater;
import hugo.weaving.DebugLog;

public class TVSearchFragment extends android.support.v17.leanback.app.SearchFragment
		implements android.support.v17.leanback.app.SearchFragment.SearchResultProvider {
	private static final int SEARCH_DELAY_MS = 300;

	@Inject
	ProviderManager providerManager;

	private MediaProvider.Filters mSearchFilter = new MediaProvider.Filters();

	private ArrayObjectAdapter mRowsAdapter;
	private Handler mHandler = new Handler();
	private SearchRunnable mDelayedLoad;
	private ListRow mLoadingRow;
	private BackgroundUpdater mBackgroundUpdater = new BackgroundUpdater();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TVButterApplication.getAppContext()
				.getComponent()
				.inject(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mBackgroundUpdater.initialise(getActivity(), R.color.black);
		ListRowPresenter mListRowPresenter = new ListRowPresenter();
		mListRowPresenter.setShadowEnabled(false);
		mRowsAdapter = new ArrayObjectAdapter(mListRowPresenter);
		setSearchResultProvider(this);
		setOnItemViewClickedListener(getDefaultItemClickedListener());
		setOnItemViewSelectedListener(new ItemViewSelectedListener());
		mDelayedLoad = new SearchRunnable();

		//setup row to use for loading
		mLoadingRow = createLoadingRow();
	}

	@Override
	public ObjectAdapter getResultsAdapter() {
		return mRowsAdapter;
	}

	private ListRow createLoadingRow() {
		HeaderItem loadingHeader = new HeaderItem(0, getString(R.string.search_results));
		ArrayObjectAdapter loadingRowAdapter = new ArrayObjectAdapter(new MediaCardPresenter(getActivity()));
		loadingRowAdapter.add(new MediaCardPresenter.MediaCardItem(true));
		return new ListRow(loadingHeader, loadingRowAdapter);
	}

	private void queryByWords(String words) {
		mRowsAdapter.clear();
		if (!TextUtils.isEmpty(words)) {
			mDelayedLoad.setSearchQuery(words);
			mHandler.removeCallbacks(mDelayedLoad);
			mHandler.postDelayed(mDelayedLoad, SEARCH_DELAY_MS);
		}
	}

	@Override
	public boolean onQueryTextChange(String newQuery) {
		if (newQuery.length() > 3)
			queryByWords(newQuery);
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		queryByWords(query);
		return true;
	}

	@DebugLog
	private void loadRows(String query) {
		//mShowsProvider.cancel();
		mRowsAdapter.clear();
		addLoadingRow();

		mSearchFilter.setKeywords(query);
		mSearchFilter.setPage(1);
		if (providerManager.hasProvider(ProviderManager.PROVIDER_TYPE_SHOW)) {
			MediaProvider mediaProvider = providerManager.getMediaProvider(ProviderManager.PROVIDER_TYPE_SHOW);
			//noinspection ConstantConditions
			mediaProvider.cancel();
			mediaProvider.getList(mSearchFilter, new MediaProvider.Callback() {
				@Override
				public void onSuccess(MediaProvider.Filters filters, ArrayList<Media> items) {
					List<MediaCardPresenter.MediaCardItem> list = MediaCardPresenter.convertMediaToOverview(items);
					addRow(getString(R.string.appow_results), list);
				}


				@Override
				public void onFailure(Exception e) {

				}
			});
		}


		if (providerManager.hasProvider(ProviderManager.PROVIDER_TYPE_MOVIE)) {
			MediaProvider mediaProvider = providerManager.getMediaProvider(ProviderManager.PROVIDER_TYPE_MOVIE);
			//noinspection ConstantConditions
			mediaProvider.cancel();
			mediaProvider.getList(mSearchFilter, new MediaProvider.Callback() {
				@Override
				public void onSuccess(MediaProvider.Filters filters, ArrayList<Media> items) {
					List<MediaCardPresenter.MediaCardItem> list = MediaCardPresenter.convertMediaToOverview(items);
					addRow(getString(R.string.movie_results), list);
				}


				@Override
				public void onFailure(Exception e) {

				}
			});
		}

	}

	private void addRow(String title, List<MediaCardPresenter.MediaCardItem> items) {
		mRowsAdapter.remove(mLoadingRow);

		HeaderItem header = new HeaderItem(0, title);
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new MediaCardPresenter(getActivity()));
		listRowAdapter.addAll(0, items);
		mRowsAdapter.add(new ListRow(header, listRowAdapter));
	}

	private void addLoadingRow() {
		mRowsAdapter.add(mLoadingRow);
	}

	protected OnItemViewClickedListener getDefaultItemClickedListener() {
		return new OnItemViewClickedListener() {
			@Override public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object object, RowPresenter.ViewHolder rowViewHolder,
					Row row) {
				if (object instanceof MediaCardPresenter.MediaCardItem) {
					MediaCardPresenter.MediaCardItem item = (MediaCardPresenter.MediaCardItem) object;
					Media media = item.getMedia();
					TVMediaDetailActivity.startActivity(getActivity(), media);
				}
			}
		};
	}

	private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
		@Override
		public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
				RowPresenter.ViewHolder rowViewHolder, Row row) {
			if (item instanceof MediaCardPresenter.MediaCardItem) {
				MediaCardPresenter.MediaCardItem overviewItem = (MediaCardPresenter.MediaCardItem) item;
				if (overviewItem.isLoading()) return;

				mBackgroundUpdater.updateBackgroundAsync(overviewItem.getMedia().headerImage);
			}
		}
	}

	private class SearchRunnable implements Runnable {

		private volatile String searchQuery;

		SearchRunnable() {
		}

		public void run() {
			loadRows(searchQuery);
		}

		void setSearchQuery(String value) {
			this.searchQuery = value;
		}
	}
}
