package io.github.fvasco.pinpoi;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.ApplicationContextHolder;


public class MainActivity extends AppCompatActivity {

    private Spinner collectionSpinner;
    private SeekBar rangeSeek;
    private TextView latitudeText;
    private TextView longitudeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ApplicationContextHolder.init(getApplicationContext());
        collectionSpinner = (Spinner) findViewById(R.id.collectionSpinner);
        rangeSeek = (SeekBar) findViewById(R.id.rangeSeek);

        final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance();
        collectionDao.open();
        try {
            ArrayAdapter<PlacemarkCollection> dataAdapter = new ArrayAdapter<PlacemarkCollection>
                    (this, R.layout.collection_item, collectionDao.findAllPlacemarkCollection()) {
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    return getView(position, convertView, parent);
                }

                @Override
                public View getView(int position, View view, ViewGroup parent) {
                    PlacemarkCollection pc = getItem(position);
                    if (view == null) {
                        view = getLayoutInflater().inflate(R.layout.collection_item, parent, false);
                    }
                    final TextView nameText = (TextView) view.findViewById(R.id.collectionItemText);
                    nameText.setText(pc.getName());
                    return view;
                }
            };

            collectionSpinner.setAdapter(dataAdapter);
        } finally {
            collectionDao.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onSearch(View view) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
