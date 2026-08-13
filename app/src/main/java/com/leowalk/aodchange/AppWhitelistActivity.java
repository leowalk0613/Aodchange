package com.leowalk.aodchange;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AppWhitelistActivity extends AppCompatActivity {

    private static final String[][] PRESETS = {
            {"网易云音乐", "com.netease.cloudmusic"},
            {"QQ音乐", "com.tencent.qqmusic"},
            {"汽水音乐", "com.luna.music"},
            {"小米音乐", "com.miui.player"},
            {"酷狗音乐", "com.kugou.android"},
            {"酷我车机版", "com.kuwo.kwmusiccar"},
            {"酷我音乐", "cn.kuwo.player"},
            {"Apple Music", "com.apple.android.music"},
            {"YouTube Music", "com.google.android.apps.youtube.music"},
            {"Spotify", "com.spotify.music"},
    };

    private static final java.util.regex.Pattern PKG_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");

    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("音乐应用白名单");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        TextView desc = new TextView(this);
        desc.setText("仅白名单内的应用播放时会显示音乐信息与歌词；其他应用（如视频、播客）不会触发显示。");
        desc.setTextSize(14);
        desc.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        desc.setLineSpacing(M3.dp(this, 2), 1f);
        desc.setPadding(0, M3.dp(this, 4), 0, M3.dp(this, 12));
        content.addView(desc);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton addApp = makeTonalButton("添加应用");
        addApp.setOnClickListener(v -> showAppPicker());
        LinearLayout.LayoutParams aap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        aap.rightMargin = M3.dp(this, 8);
        btnRow.addView(addApp, aap);

        MaterialButton addPkg = makeTonalButton("添加包名");
        addPkg.setOnClickListener(v -> showAddPackageDialog());
        LinearLayout.LayoutParams app = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(addPkg, app);
        content.addView(btnRow);

        MaterialButton reset = makeTonalButton("恢复默认");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = M3.dp(this, 8);
        rp.bottomMargin = M3.dp(this, 12);
        reset.setOnClickListener(v -> {
            saveWhitelist(defaultList());
            refreshList();
        });
        content.addView(reset, rp);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshList();
    }

    private MaterialButton makeTonalButton(String label) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                M3.attrColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930)));
        btn.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{}, },
                new int[]{M3.attrColor(this, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF)}));
        return btn;
    }

    private void refreshList() {
        listContainer.removeAllViews();
        List<String> list = loadWhitelist();
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("白名单为空，可通过上方按钮添加音乐应用");
            empty.setTextSize(14);
            empty.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
            empty.setPadding(0, M3.dp(this, 12), 0, 0);
            listContainer.addView(empty);
            return;
        }
        for (String pkg : list) {
            listContainer.addView(buildAppRow(pkg));
        }
    }

    private View buildAppRow(final String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(M3.dp(this, 4), M3.dp(this, 10), M3.dp(this, 4), M3.dp(this, 10));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        } catch (Exception e) {
            icon.setImageDrawable(null);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(M3.dp(this, 40), M3.dp(this, 40));
        row.addView(icon, ilp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(appLabel(pkg));
        label.setTextSize(16);
        label.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        textCol.addView(label);
        TextView pkgTv = new TextView(this);
        pkgTv.setText(pkg);
        pkgTv.setTextSize(12);
        pkgTv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        textCol.addView(pkgTv);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = new TextView(this);
        remove.setText("✕");
        remove.setTextSize(18);
        remove.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        remove.setPadding(M3.dp(this, 12), 0, M3.dp(this, 4), 0);
        remove.setOnClickListener(v -> {
            List<String> list = loadWhitelist();
            list.remove(pkg);
            saveWhitelist(list);
            refreshList();
        });
        row.addView(remove);

        return row;
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void showAppPicker() {
        List<AppEntry> all = loadInstalledApps();
        if (all.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage("没有可添加的应用")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final List<AppEntry> filtered = new ArrayList<>(all);

        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(M3.dp(this, 20), M3.dp(this, 8), M3.dp(this, 20), M3.dp(this, 4));

        EditText search = new EditText(this);
        search.setHint("搜索应用或包名");
        search.setSingleLine(true);
        search.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        search.setHintTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        dialogView.addView(search);

        ListView list = new ListView(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 360));
        list.setLayoutParams(llp);
        dialogView.addView(list);

        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public Object getItem(int i) { return filtered.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                AppEntry e = filtered.get(i);
                LinearLayout row = new LinearLayout(AppWhitelistActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, M3.dp(AppWhitelistActivity.this, 8), 0, M3.dp(AppWhitelistActivity.this, 8));
                ImageView ic = new ImageView(AppWhitelistActivity.this);
                ic.setImageDrawable(e.icon);
                row.addView(ic, new LinearLayout.LayoutParams(M3.dp(AppWhitelistActivity.this, 40), M3.dp(AppWhitelistActivity.this, 40)));
                LinearLayout col = new LinearLayout(AppWhitelistActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView l = new TextView(AppWhitelistActivity.this);
                l.setText(e.label);
                l.setTextSize(15);
                l.setTextColor(M3.attrColor(AppWhitelistActivity.this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
                col.addView(l);
                TextView p = new TextView(AppWhitelistActivity.this);
                p.setText(e.packageName);
                p.setTextSize(12);
                p.setTextColor(M3.attrColor(AppWhitelistActivity.this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
                col.addView(p);
                row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> {
            AppEntry e = filtered.get(pos);
            List<String> whitelist = loadWhitelist();
            if (!whitelist.contains(e.packageName)) {
                whitelist.add(e.packageName);
                saveWhitelist(whitelist);
            }
            refreshList();
            android.app.Dialog d = (android.app.Dialog) list.getTag();
            if (d != null) d.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                filtered.clear();
                if (q.isEmpty()) filtered.addAll(all);
                else for (AppEntry e : all) {
                    if (e.label.toLowerCase().contains(q) || e.packageName.toLowerCase().contains(q)) {
                        filtered.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle("选择应用")
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null);
        androidx.appcompat.app.AlertDialog d = b.create();
        list.setTag(d);
        d.show();
    }

    private void showAddPackageDialog() {
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(M3.dp(this, 20), M3.dp(this, 8), M3.dp(this, 20), M3.dp(this, 4));

        EditText input = new EditText(this);
        input.setHint("例如 com.netease.cloudmusic");
        input.setSingleLine(true);
        input.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        input.setHintTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        dialogView.addView(input);

        final TextView err = new TextView(this);
        err.setTextSize(12);
        err.setTextColor(0xFFFFB4AB);
        err.setPadding(0, M3.dp(this, 4), 0, 0);
        dialogView.addView(err);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle("添加包名")
                .setMessage("可手动输入音乐应用的包名，未安装的应用也会保留在白名单中。")
                .setView(dialogView)
                .setPositiveButton("添加", null)
                .setNegativeButton(android.R.string.cancel, null);
        androidx.appcompat.app.AlertDialog d = b.create();
        d.setOnShowListener(dl -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pkg = input.getText().toString().trim();
                    if (pkg.isEmpty()) { err.setText("请输入包名"); return; }
                    if (!PKG_PATTERN.matcher(pkg).matches()) { err.setText("包名格式不正确"); return; }
                    List<String> list = loadWhitelist();
                    if (list.contains(pkg)) { err.setText("该包名已在白名单中"); return; }
                    list.add(pkg);
                    saveWhitelist(list);
                    refreshList();
                    d.dismiss();
                }));
        d.show();
    }

    private static class AppEntry {
        String packageName;
        String label;
        Drawable icon;
        AppEntry(String p, String l, Drawable i) { packageName = p; label = l; icon = i; }
    }

    private List<AppEntry> loadInstalledApps() {
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> infos;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            infos = pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(
                    android.content.pm.PackageManager.GET_META_DATA));
        } else {
            infos = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
        }
        Set<String> whitelist = new TreeSet<>(loadWhitelist());
        List<AppEntry> out = new ArrayList<>();
        for (android.content.pm.ApplicationInfo ai : infos) {
            if (ai.packageName.equals(getPackageName())) continue;
            if (whitelist.contains(ai.packageName)) continue;
            boolean system = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system && !updated && pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            try {
                String label = pm.getApplicationLabel(ai).toString();
                Drawable icon = pm.getApplicationIcon(ai);
                out.add(new AppEntry(ai.packageName, label, icon));
            } catch (Throwable ignored) {}
        }
        out.sort((a, b) -> a.label.toLowerCase().compareTo(b.label.toLowerCase()));
        return out;
    }

    private List<String> defaultList() {
        List<String> list = new ArrayList<>();
        for (String[] p : PRESETS) list.add(p[1]);
        return list;
    }

    private List<String> loadWhitelist() {
        return SettingsHelper.getWhitelist(this);
    }

    private void saveWhitelist(List<String> list) {
        SettingsHelper.saveWhitelist(this, list);
    }

    private void writeSetting(String key, Object value) {
        SettingsHelper.writeSetting(this, key, value);
    }
}
