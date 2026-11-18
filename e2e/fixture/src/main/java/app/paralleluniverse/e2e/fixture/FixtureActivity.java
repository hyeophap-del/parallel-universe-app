package app.paralleluniverse.e2e.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** 런처에 보이기만 하면 되는 빈 화면. */
public class FixtureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText("E2E Fixture");
        setContentView(view);
    }
}
