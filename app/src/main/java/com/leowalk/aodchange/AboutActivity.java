package com.leowalk.aodchange;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class AboutActivity extends AppCompatActivity {

    private static final String PROJECT_URL = "https://github.com/leowalk0613/aodchange";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("关于");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        content.addView(M3.title(this, "AodChange"));

        LinearLayout info = M3.cardContent(this);
        info.addView(addInfoRow("版本号", version()));
        info.addView(M3.clickRow(this, "项目地址", PROJECT_URL,
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))));
        content.addView(M3.card(this, info));

        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "未知";
        }
    }

    private LinearLayout addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(15);
        v.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        v.setGravity(Gravity.RIGHT);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        row.addView(v);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = M3.dp(this, 8);
        row.setLayoutParams(rlp);
        return row;
    }
}