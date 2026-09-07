package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility redirect for older notification/shortcut entry points. */
public final class MobileMainActivityV2 extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, DailyMainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
