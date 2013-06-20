package net.bicou.redmine.app.issues;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.google.gson.Gson;
import net.bicou.redmine.Constants;
import net.bicou.redmine.R;
import net.bicou.redmine.data.Server;
import net.bicou.redmine.data.json.Issue;
import net.bicou.redmine.data.sqlite.IssuesDbAdapter;
import net.bicou.redmine.data.sqlite.ServersDbAdapter;
import net.bicou.redmine.util.L;

import java.util.Locale;

public class IssueFragment extends SherlockFragment {
	public static final String KEY_ISSUE_JSON = "net.bicou.redmine.Issue";

	private Issue mIssue;

	private OnPageChangeListener mListener;
	private IssueTabsAdapter mAdapter;

	private static final int NB_TABS = 2;
	private static final String[] TAB_TITLES = new String[NB_TABS];

	public interface FragmentActivationListener {
		void onFragmentActivated();
	}

	public static IssueFragment newInstance(final Bundle args) {
		final IssueFragment f = new IssueFragment();
		f.setArguments(args);
		return f;
	}

	private static class IssueTabsAdapter extends FragmentPagerAdapter {
		Fragment[] mFragments = new Fragment[NB_TABS];
		Bundle args;

		public IssueTabsAdapter(final FragmentManager fm, final Bundle args) {
			super(fm);
			this.args = args;
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public CharSequence getPageTitle(final int position) {
			return TAB_TITLES[position].toUpperCase(Locale.getDefault());
		}

		/**
		 * {{@link #getItem(int)} is a VERY bad name: it is not meant to retrieve the item, but it is meant to INSTANTIATE the item.<br />
		 * Hence, this method will simply retrieve the item.
		 *
		 * @param position
		 * @return The {@code Fragment} at that position
		 */
		public Fragment getFragment(final int position) {
			return mFragments[position];
		}

		@Override
		public Fragment getItem(final int position) {
			if (position == 0) {
				mFragments[position] = IssueOverviewFragment.newInstance(args);
			} else {
				mFragments[position] = IssueHistoryFragment.newInstance(args);
			}
			return mFragments[position];
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.frag_issue, container, false);
		L.d("");

		int i = 0;
		TAB_TITLES[i++] = getString(R.string.issue_overview_title);
		TAB_TITLES[i++] = getString(R.string.issue_journal_title);

		final Bundle args = new Bundle(getArguments());
		if (savedInstanceState == null) {
			// Load issue
			final Activity activity = getActivity();
			final ServersDbAdapter sdb = new ServersDbAdapter(activity);
			sdb.open();
			final Server server = sdb.getServer(args.getLong(Constants.KEY_SERVER_ID, 0));
			sdb.close();

			if (server == null) {
				L.e("Server can't be null now!", null);
				return v;
			}

			final IssuesDbAdapter db = new IssuesDbAdapter(activity);
			db.open();
			mIssue = db.select(server, args.getLong(Constants.KEY_ISSUE_ID), null);
			db.close();
		} else {
			mIssue = new Gson().fromJson(savedInstanceState.getString(KEY_ISSUE_JSON), Issue.class);
		}
		args.putString(KEY_ISSUE_JSON, new Gson().toJson(mIssue));

		// Adapter
		mAdapter = new IssueTabsAdapter(getChildFragmentManager(), args);

		// Listener
		mListener = new OnPageChangeListener() {
			@Override
			public void onPageSelected(final int position) {
				final Fragment f = mAdapter.getFragment(position);
				if (f instanceof FragmentActivationListener) {
					((FragmentActivationListener) f).onFragmentActivated();
				}
			}

			@Override
			public void onPageScrolled(final int arg0, final float arg1, final int arg2) {
			}

			@Override
			public void onPageScrollStateChanged(final int arg0) {
			}
		};

		// Bind everything
		final ViewPager pager = (ViewPager) v.findViewById(R.id.issue_pager);
		pager.setAdapter(mAdapter);
		pager.setOnPageChangeListener(mListener);

		setHasOptionsMenu(true);

		return v;
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mIssue != null) {
			final String json = new Gson().toJson(mIssue);
			outState.putString(KEY_ISSUE_JSON, json);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (getActivity() != null && mIssue != null) {
			getSherlockActivity().getSupportActionBar().setTitle("#" + mIssue.id);
		}
		getSherlockActivity().supportInvalidateOptionsMenu();
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		getSherlockActivity().supportInvalidateOptionsMenu();
	}

	public Issue getIssue() {
		return mIssue;
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_issue, menu);
	}
}