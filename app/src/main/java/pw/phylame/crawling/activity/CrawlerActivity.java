package pw.phylame.crawling.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import pw.phylame.crawling.CrawlerApp;
import pw.phylame.crawling.HistoryFragment;
import pw.phylame.crawling.R;
import pw.phylame.crawling.task.TaskFragment;
import pw.phylame.support.Activities;
import pw.phylame.support.TimedAction;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {
    private TimedAction mExitAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crawler);
        setSupportActionBar(viewById(this, R.id.toolbar));
        findViewById(R.id.fab).setOnClickListener(view -> {
            Snackbar.make(view, "Create New Task", Snackbar.LENGTH_SHORT).show();
        });
        findViewById(R.id.bottom_bar).setOnClickListener(view -> {
            Activities.startActivity(this, HistoryActivity.class);
        });
        mExitAction = new TimedAction(getResources().getInteger(R.integer.exit_check_millis));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_crawler, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                Activities.startActivity(this, NewTaskActivity.class);
                break;
            case R.id.action_edit:
                break;
            case R.id.action_settings:
                Activities.startActivity(this, SettingsActivity.class);
                break;
            case R.id.action_exit:
                exitApp();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mExitAction.isEnable()) {
            exitApp();
        } else {
            Toast.makeText(this, R.string.exit_check_tip, Toast.LENGTH_SHORT).show();
        }
    }

    private void exitApp() {
        CrawlerApp.sharedApp().finish();
        System.exit(0);
    }
}
