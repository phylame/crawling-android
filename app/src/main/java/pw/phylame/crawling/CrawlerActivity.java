package pw.phylame.crawling;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import pw.phylame.crawling.downloading.DownloadingFragment;
import pw.phylame.support.Activities;

import static pw.phylame.support.Views.viewById;

public class CrawlerActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crawler);
        setSupportActionBar(viewById(this, R.id.toolbar));

        final ViewPager viewPager = viewById(this, R.id.pager);
        viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                return position == 0
                        ? DownloadingFragment.newInstance()
                        : DownloadedFragment.newInstance();
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return position == 0
                        ? getString(R.string.pager_downloading_title)
                        : getString(R.string.pager_downloaded_title);
            }

            @Override
            public int getCount() {
                return 2;
            }
        });
        TabLayout tabLayout = viewById(this, R.id.tab);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

//        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_crawler, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Activities.startActivity(this, SettingsActivity.class);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
