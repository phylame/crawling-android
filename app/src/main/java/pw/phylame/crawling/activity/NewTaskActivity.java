package pw.phylame.crawling.activity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import jem.epm.EpmManager;
import lombok.val;
import pw.phylame.commons.util.CollectionUtils;
import pw.phylame.crawling.R;

import static pw.phylame.support.Views.viewById;

public class NewTaskActivity extends BaseActivity {
    private TextView mURL;
    private Spinner mFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        initActionBar();

        mURL = viewById(this, R.id.url);
        mFormat = viewById(this, R.id.formats);
        mFormat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, CollectionUtils.listOf(EpmManager.supportedMakers())));
    }

    private void initActionBar() {
        setSupportActionBar(viewById(this, R.id.toolbar));
        val actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_new_task, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                val url = mURL.getText().toString();
                if (url.isEmpty()) {
                    Toast.makeText(this, "please input url", Toast.LENGTH_SHORT).show();
                    mURL.requestFocus();
                    return true;
                }
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
