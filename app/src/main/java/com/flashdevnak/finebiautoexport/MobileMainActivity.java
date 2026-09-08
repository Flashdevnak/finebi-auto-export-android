package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Compatibility redirect for old task-stack/activity records from pre-Daily builds.
 * Keeping this class as a redirect prevents Android from restoring the legacy UI
 * after an install-over or after navigating back from an older task.
 */
public final class MobileMainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, DailyMainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
