package net.bicou.redmine.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;

import net.bicou.redmine.app.ssl.SupportSSLKeyManager;
import net.bicou.redmine.data.Server;
import net.bicou.redmine.data.json.IssueCategoriesList;
import net.bicou.redmine.data.json.Project;
import net.bicou.redmine.data.json.ProjectsList;
import net.bicou.redmine.data.json.VersionsList;
import net.bicou.redmine.data.sqlite.DbAdapter;
import net.bicou.redmine.data.sqlite.ProjectsDbAdapter;
import net.bicou.redmine.data.sqlite.ServersDbAdapter;
import net.bicou.redmine.platform.ProjectManager;
import net.bicou.redmine.util.L;

/**
 * Service to handle Account sync. This is invoked with an intent with action ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its IBinder.
 */
public class ProjectsSyncAdapterService extends Service {
	public static final String SYNC_MARKER_KEY = "net.bicou.redmine.sync.Projects.marker";
	private static final Object sSyncAdapterLock = new Object();

	private static SyncAdapter sSyncAdapter = null;

	@Override
	public void onCreate() {
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
			}
		}
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return sSyncAdapter.getSyncAdapterBinder();
	}

	/**
	 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the platform ContactOperations provider. This sample shows a basic 2-way sync between
	 * the
	 * client and a sample server. It also contains an example of how to update the contacts' status messages, which would be useful for a messaging or social
	 * networking client.
	 */
	private static class SyncAdapter extends AbstractThreadedSyncAdapter {
		private final AccountManager mAccountManager;
		private final Context mContext;

		public SyncAdapter(final Context context, final boolean autoInitialize) {
			super(context, autoInitialize);
			mContext = context;
			mAccountManager = AccountManager.get(context);
		}

		@Override
		public void onPerformSync(final Account account, final Bundle extras, final String authority, final ContentProviderClient provider, final SyncResult syncResult) {
			final long lastSyncMarker = getServerSyncMarker(account);

			// Get server ID
			final ServersDbAdapter sdb = new ServersDbAdapter(mContext);
			sdb.open();
			final Server server = sdb.getServer(account.name);
			sdb.close();

			// Init SSL and certificates
			SupportSSLKeyManager.init(mContext);

			if (server == null) {
				L.e("Couldn't get the server for account: " + account, null);
				return;
			}

			long newSyncState = new Synchronizer(mContext).synchronizeProjects(server, lastSyncMarker);
			if (newSyncState > 0) {
				setServerSyncMarker(account, newSyncState);
			}
		}

		/**
		 * This helper function fetches the last known high-water-mark we received from the server - or 0 if we've never synced.
		 *
		 * @param account the account we're syncing
		 *
		 * @return the change high-water-mark
		 */
		private long getServerSyncMarker(final Account account) {
			final String markerString = mAccountManager.getUserData(account, SYNC_MARKER_KEY);
			if (!TextUtils.isEmpty(markerString)) {
				return Long.parseLong(markerString);
			}
			return 0;
		}

		/**
		 * Save off the high-water-mark we receive back from the server.
		 *
		 * @param account The account we're syncing
		 * @param marker  The high-water-mark we want to save.
		 */
		private void setServerSyncMarker(final Account account, final long marker) {
			mAccountManager.setUserData(account, SYNC_MARKER_KEY, Long.toString(marker));
		}
	}

	public static class Synchronizer {
		private final Context mContext;

		public Synchronizer(Context context) {
			mContext = context;
		}

		public long synchronizeProjects(final Server server, long lastSyncMarker) {
			final long newSyncState;
			DbAdapter db = new ProjectsDbAdapter(mContext);
			db.open();

			// Sync projects
			final ProjectsList projects = NetworkUtilities.syncProjects(mContext, server);
			if (projects != null && projects.projects != null && projects.projects.size() > 0) {
				newSyncState = ProjectManager.updateProjects(db, server, projects.projects, lastSyncMarker);

				VersionsList versionsList;
				IssueCategoriesList categories;
				Project projectWithTrackers;

				for (final Project project : projects.projects) {
					if (!project.is_sync_blocked) {
						// Sync versions
						versionsList = NetworkUtilities.syncVersions(mContext, server, project.id);
						if (versionsList != null && versionsList.versions != null && versionsList.versions.size() > 0) {
							ProjectManager.updateVersions(db, server, versionsList.versions);
						}

						// Sync project trackers
						projectWithTrackers = NetworkUtilities.syncProjectTrackers(mContext, server, project);
						if (projectWithTrackers != null && projectWithTrackers.trackers != null && projectWithTrackers.trackers.size() > 0) {
							ProjectManager.updateProjectTrackers(db, server, projectWithTrackers, projectWithTrackers.trackers);
						}

						// Sync issue categories
						categories = NetworkUtilities.syncIssueCategories(mContext, server, project);
						if (categories != null && categories.issue_categories != null && categories.issue_categories.size() > 0) {
							ProjectManager.updateIssueCategories(db, server, project, categories.issue_categories);
						}
					}
				}
			} else {
				newSyncState = 0;
			}

			db.close();

			return newSyncState;
		}
	}
}
