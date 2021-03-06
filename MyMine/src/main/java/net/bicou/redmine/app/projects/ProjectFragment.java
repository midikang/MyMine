package net.bicou.redmine.app.projects;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.gson.Gson;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;
import com.origamilabs.library.views.StaggeredGridView;

import net.bicou.redmine.Constants;
import net.bicou.redmine.R;
import net.bicou.redmine.app.AsyncTaskFragment;
import net.bicou.redmine.app.ga.TrackedFragment;
import net.bicou.redmine.app.issues.IssuesActivity;
import net.bicou.redmine.app.issues.IssuesListFilter;
import net.bicou.redmine.app.issues.edit.EditIssueActivity;
import net.bicou.redmine.app.issues.edit.IssueUploader;
import net.bicou.redmine.app.welcome.CardsAdapter;
import net.bicou.redmine.app.welcome.OverviewCard;
import net.bicou.redmine.app.wiki.WikiUtils;
import net.bicou.redmine.data.Server;
import net.bicou.redmine.data.json.Project;
import net.bicou.redmine.data.json.Tracker;
import net.bicou.redmine.data.sqlite.DbAdapter;
import net.bicou.redmine.data.sqlite.IssuesDbAdapter;
import net.bicou.redmine.data.sqlite.ProjectsDbAdapter;
import net.bicou.redmine.data.sqlite.TrackersDbAdapter;
import net.bicou.redmine.util.L;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class ProjectFragment extends TrackedFragment {
	public static final String KEY_PROJECT_JSON = "net.bicou.redmine.Project";
	private static final int ADD_ISSUE = 0;

	private Project mProject;
	TextView mUpdatedOn, mCreatedOn, mTitle, mDescription, mServer, mParent;
	View mFullDescription;
	CheckBox mIsFavorite;
	CardsAdapter mAdapter;
	StaggeredGridView mStaggeredGridView;
	DateFormat mLongDateFormat = DateFormat.getDateInstance(DateFormat.LONG);
	private Animator mAnimator;

	public static ProjectFragment newInstance(final Bundle args) {
		final ProjectFragment f = new ProjectFragment();
		String log = "args: ";
		for (final String key : args.keySet()) {
			log += key + "=" + (args.get(key) == null ? "null" : "not null") + ", ";
		}
		L.d(log);
		f.setArguments(args);
		return f;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.frag_project_overview, container, false);
		L.d("");

		mCreatedOn = (TextView) v.findViewById(R.id.project_overview_created_on);
		mUpdatedOn = (TextView) v.findViewById(R.id.project_overview_updated_on);
		mServer = (TextView) v.findViewById(R.id.project_overview_server);
		mTitle = (TextView) v.findViewById(R.id.project_overview_title);
		mParent = (TextView) v.findViewById(R.id.project_overview_parent);
		mDescription = (TextView) v.findViewById(R.id.project_overview_description);
		mFullDescription = v.findViewById(R.id.project_overview_full_description);
		mIsFavorite = (CheckBox) v.findViewById(R.id.project_overview_is_favorite);

		//Add onPreDrawListener
		mFullDescription.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				mFullDescription.getViewTreeObserver().removeOnPreDrawListener(this);
				mFullDescription.setVisibility(View.GONE);

				final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
				final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
				mFullDescription.measure(widthSpec, heightSpec);

				mAnimator = slideAnimator(0, mFullDescription.getMeasuredHeight());
				return true;
			}
		});

		mTitle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mFullDescription.getVisibility() == View.GONE) {
					expand();
				} else {
					collapse();
				}
			}
		});

		mIsFavorite.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				mProject.is_favorite = !mProject.is_favorite;
				ProjectsDbAdapter db = new ProjectsDbAdapter(getActivity());
				db.open();
				db.update(mProject);
				db.close();
				refreshUI();
			}
		});

		mAdapter = new CardsAdapter();
		mAdapter.setCallbacks(new CardsAdapter.CardActionCallback() {
			@Override
			public void onActionSelected(final Object action) {
				if (action instanceof Integer && (Integer) action == ADD_ISSUE) {
					Intent intent = new Intent(getActivity(), EditIssueActivity.class);
					intent.putExtra(Constants.KEY_PROJECT, mProject);
					intent.putExtra(Constants.KEY_SERVER, mProject.server);
					getActivity().startActivityForResult(intent, IssueUploader.CREATE_ISSUE);
				}
			}
		});
		mStaggeredGridView = (StaggeredGridView) v.findViewById(R.id.project_overview_container);
		mStaggeredGridView.setAdapter(mAdapter);
		mStaggeredGridView.setOnItemClickListener(mAdapter.getStaggeredItemClickListener());
		mStaggeredGridView.setClickable(true);
		mStaggeredGridView.setDrawSelectorOnTop(true);
		mStaggeredGridView.setLongClickable(true);

		final Bundle args = getArguments();
		if (savedInstanceState == null) {
			if (args.keySet().contains(KEY_PROJECT_JSON)) {
				mProject = new Gson().fromJson(args.getString(KEY_PROJECT_JSON), Project.class);
			} else {
				// Load project
				final long serverId = args.getLong(Constants.KEY_SERVER_ID);
				final ProjectsDbAdapter db = new ProjectsDbAdapter(getActivity());
				db.open();
				mProject = db.select(serverId, args.getLong(Constants.KEY_PROJECT_ID), null);
				db.close();
			}
		} else {
			mProject = new Gson().fromJson(savedInstanceState.getString(KEY_PROJECT_JSON), Project.class);
		}

		AsyncTaskFragment.runTask((ActionBarActivity) getActivity(), ProjectsActivity.ACTION_LOAD_PROJECT_CARDS, mProject);

		return v;
	}

	private void expand() {
		//set Visible
		mFullDescription.setVisibility(View.VISIBLE);
		Drawable arrow = getResources().getDrawable(android.R.drawable.arrow_up_float);
		mTitle.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null);
		mAnimator.start();
	}

	private void collapse() {
		Drawable arrow = getResources().getDrawable(android.R.drawable.arrow_down_float);
		mTitle.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null);
		int finalHeight = mFullDescription.getHeight();
		ValueAnimator mAnimator = slideAnimator(finalHeight, 0);
		mAnimator.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationEnd(Animator animator) {
				mFullDescription.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationStart(Animator animator) {
			}

			@Override
			public void onAnimationCancel(Animator animator) {
			}

			@Override
			public void onAnimationRepeat(Animator animator) {
			}
		});
		mAnimator.start();
	}

	private ValueAnimator slideAnimator(int start, int end) {
		ValueAnimator animator = ValueAnimator.ofInt(start, end);
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				//Update Height
				int value = (Integer) valueAnimator.getAnimatedValue();

				ViewGroup.LayoutParams layoutParams = mFullDescription.getLayoutParams();
				layoutParams.height = value;
				mFullDescription.setLayoutParams(layoutParams);
			}
		});
		return animator;
	}

	public static List<OverviewCard> getProjectCards(Context context, Server server, Project project) {
		List<OverviewCard> cards = new ArrayList<OverviewCard>();

		DbAdapter db = new ProjectsDbAdapter(context);
		db.open();
		getIssuesCard(db, context, cards, server, project);
		db.close();

		return cards;
	}

	private static void getIssuesCard(DbAdapter db, Context context, List<OverviewCard> cards, Server server, Project project) {
		// Get DBs
		TrackersDbAdapter tdb = new TrackersDbAdapter(db);
		IssuesDbAdapter idb = new IssuesDbAdapter(db);

		// Create card click intent
		Intent intent = new Intent(context, IssuesActivity.class);
		IssuesListFilter filter = new IssuesListFilter(server.rowId, IssuesListFilter.FilterType.PROJECT, project.id);
		Bundle args = new Bundle();
		filter.saveTo(args);
		intent.putExtras(args);

		// Count issues
		StringBuilder issues = new StringBuilder();
		List<Tracker> trackers = tdb.selectAll(server, project.id);
		Point nbIssues;
		for (Tracker tracker : trackers) {
			nbIssues = idb.countIssues(project, tracker);
			if (nbIssues.x + nbIssues.y > 0) {
				issues.append(String.format(context.getString(R.string.project_overview_issues_card_tracker), tracker.name, nbIssues.y, nbIssues.x)).append("\n");
			}
		}

		// Create the card
		OverviewCard issuesCard = new OverviewCard(intent);
		issuesCard.setContent(R.string.title_issues, issues.toString().trim(), 0, R.drawable.icon_issues);
		issuesCard.addAction(ADD_ISSUE, R.string.menu_issues_add);
		cards.add(issuesCard);
	}

	public void onCardsBuilt(List<OverviewCard> cards) {
		mAdapter.setData(cards);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mProject != null) {
			final String json = new Gson().toJson(mProject);
			outState.putString(KEY_PROJECT_JSON, json);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshUI();
	}

	private void refreshUI() {
		if (getActivity() != null && mProject != null) {
			((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(mProject.name);
			mTitle.setText(mProject.name);
			mCreatedOn.setText(Html.fromHtml(getString(R.string.project_created_on, mLongDateFormat.format(mProject.created_on.getTime()))));
			mUpdatedOn.setText(Html.fromHtml(getString(R.string.project_updated_on, mLongDateFormat.format(mProject.updated_on.getTime()))));
			mServer.setText(Html.fromHtml(getString(R.string.project_server, mProject.server.serverUrl)));
			if (mProject.parent == null || mProject.parent.id <= 0) {
				mParent.setVisibility(View.GONE);
			} else {
				mParent.setText(Html.fromHtml(getString(R.string.project_parent, mProject.parent.name)));
			}
			if (!TextUtils.isEmpty(mProject.description)) {
				Spanned description = Html.fromHtml(WikiUtils.htmlFromTextile(mProject.description));
				mDescription.setText(description);
			}
			mIsFavorite.setChecked(mProject.is_favorite);
		}
	}
}
