package chin.com.frdict.activity;

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;

import chin.com.frdict.R;
import chin.com.frdict.SettingsFragment;

/**
 * Settings Activity
 *
 * Created by Chin on 20-Nov-16.
 */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        SettingsFragment frag = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();
    }
}
