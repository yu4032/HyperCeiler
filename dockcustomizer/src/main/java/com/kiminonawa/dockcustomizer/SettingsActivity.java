package com.kiminonawa.dockcustomizer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.File;

public class SettingsActivity extends Activity {

    private int mode = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Read saved mode
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = sp.getString("light_mode", "fixed");
        if ("none".equals(saved)) mode = 0;
        else if ("dynamic".equals(saved)) mode = 2;
        else mode = 1;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 96, 48, 48);
        layout.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText("DockCustomizer");
        title.setTextSize(22);
        layout.addView(title);

        RadioGroup group = new RadioGroup(this);
        String[] labels = {"No Light", "Fixed Light", "Dynamic Light"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setId(i);
            rb.setChecked(i == mode);
            group.addView(rb);
        }
        group.setOnCheckedChangeListener((g, id) -> mode = id);
        layout.addView(group);

        Button apply = new Button(this);
        apply.setText("Apply & Restart Launcher");
        apply.setOnClickListener(v -> {
            saveAndRestart();
        });
        layout.addView(apply);

        setContentView(layout);

        // Ensure prefs file is world-readable
        try {
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, getPackageName() + "_preferences.xml");
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            prefsFile.setReadable(true, false);
        } catch (Exception ignored) {}
    }

    private void saveAndRestart() {
        String val = mode == 0 ? "none" : mode == 2 ? "dynamic" : "fixed";

        // Write via root — guaranteed to work
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo '" + val + "' > /sdcard/dock_light.txt\n");
            os.writeBytes("am force-stop com.miui.home\n");
            os.writeBytes("sleep 1\n");
            os.writeBytes("am start -n com.miui.home/.launcher.Launcher\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            Toast.makeText(this, "Done: " + val, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Need root", Toast.LENGTH_SHORT).show();
        }
    }
}
