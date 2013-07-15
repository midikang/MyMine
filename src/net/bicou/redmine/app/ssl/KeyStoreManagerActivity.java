package net.bicou.redmine.app.ssl;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.support.v4.app.Fragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import net.bicou.android.splitscreen.SplitActivity;
import net.bicou.redmine.R;
import net.bicou.redmine.app.AsyncTaskFragment;
import net.bicou.redmine.app.misc.EmptyFragment;
import net.bicou.redmine.net.ssl.MyMineSSLKeyManager;
import net.bicou.redmine.util.PreferencesManager;

public class KeyStoreManagerActivity extends SplitActivity<CertificatesListFragment, CertificateFragment> implements AsyncTaskFragment.TaskFragmentCallbacks {
	@Override
	protected Fragment createEmptyFragment(Bundle args) {
		return EmptyFragment.newInstance(R.drawable.empty_cert);
	}

	@Override
	protected CertificatesListFragment createMainFragment(Bundle args) {
		return CertificatesListFragment.newInstance(args);
	}

	@Override
	protected CertificateFragment createContentFragment(Bundle args) {
		return CertificateFragment.newInstance(args);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setSupportProgressBarIndeterminate(true);
		setSupportProgressBarIndeterminateVisibility(false);
		super.onCreate(savedInstanceState);
		
		AsyncTaskFragment.attachAsyncTaskFragment(this);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.menu_certificates, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_certificate_add:
			startActivity(new Intent(this, AddNewCertificateActivity.class));
			return true;

		case R.id.menu_certificate_import:
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				importCertificate();
			}
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void saveAlias(final String alias) {
		PreferencesManager.setString(this, MyMineSSLKeyManager.KEY_CERTIFICATE_ALIAS, alias);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final CertificatesListFragment f = getMainFragment();
				if (f != null) {
					f.refreshList();
				}
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void importCertificate() {
		KeyChain.choosePrivateKeyAlias(this, new KeyChainAliasCallback() {
			@Override
			public void alias(final String alias) {
				// Credential alias selected. Remember the alias selection for future use.
				if (alias != null) {
					saveAlias(alias);
				}
			}
		}, new String[] {
				"RSA",
				"DSA"
		}, // List of acceptable key types. null for any
				null, // issuer, null for any
				null,// "internal.example.com", // host name of server requesting the cert, null if unavailable
				-1,// 443, // port of server requesting the cert, -1 if unavailable
				null); // alias to preselect, null if unavailable
	}

	@Override
	public void onPreExecute(final int action, final Object parameters) {
		setSupportProgressBarIndeterminateVisibility(true);
	}

	@Override
	public Object doInBackGround(final int action, final Object parameters) {
		if (getContentFragment() != null) {
			getContentFragment().loadCertificate((String) parameters);
		}
		return null;
	}

	@Override
	public void onPostExecute(final int action, final Object parameters, final Object result) {
		setSupportProgressBarIndeterminateVisibility(false);
		if (getContentFragment() != null) {
			getContentFragment().refreshUI();
		}
	}
}
