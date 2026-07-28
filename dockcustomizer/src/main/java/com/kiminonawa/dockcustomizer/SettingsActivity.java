package com.kiminonawa.dockcustomizer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataOutputStream;

public class SettingsActivity extends Activity {

    private int mode = 1;
    private EditText blurRadiusInput, heightOffsetInput, widthOffsetInput, cornerInput;
    private int blurRadius = 100, heightOffset, widthOffset, cornerOffset = -1;
    private CheckBox squircleCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = sp.getString("light_mode", "fixed");
        if ("none".equals(saved)) mode = 0;
        else if ("dynamic".equals(saved)) mode = 2;

        blurRadius = sp.getInt("blur_radius", 100);
        heightOffset = sp.getInt("height_offset", 0);
        widthOffset = sp.getInt("width_offset", 0);
        cornerOffset = sp.getInt("corner_offset", -1);
        boolean squircle = sp.getBoolean("squircle", false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 96, 48, 48);
        layout.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText("DockCustomizer");
        title.setTextSize(22);
        layout.addView(title);

        layout.addView(label("Light Mode"));
        RadioGroup group = new RadioGroup(this);
        String[] labels = {"No Light", "Fixed Light", "Dynamic Light"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]); rb.setId(i); rb.setChecked(i == mode);
            group.addView(rb);
        }
        group.setOnCheckedChangeListener((g, id) -> mode = id);
        layout.addView(group);

        layout.addView(label("Blur Radius (0-400)"));
        blurRadiusInput = new EditText(this);
        blurRadiusInput.setText(String.valueOf(blurRadius));
        blurRadiusInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(blurRadiusInput);

        layout.addView(label("Height Offset (px, 0=default)"));
        heightOffsetInput = new EditText(this);
        heightOffsetInput.setText(String.valueOf(heightOffset));
        heightOffsetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(heightOffsetInput);

        layout.addView(label("Width Offset (px, 0=default)"));
        widthOffsetInput = new EditText(this);
        widthOffsetInput.setText(String.valueOf(widthOffset));
        widthOffsetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(widthOffsetInput);

        layout.addView(label("Corner Radius Offset (px, negative=smaller)"));
        cornerInput = new EditText(this);
        cornerInput.setText(String.valueOf(cornerOffset));
        cornerInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(cornerInput);

        squircleCheck = new CheckBox(this);
        squircleCheck.setText("Squircle Corners (iOS-style continuous curve)");
        squircleCheck.setChecked(squircle);
        layout.addView(squircleCheck);

        Button apply = new Button(this);
        apply.setText("Apply & Restart Launcher");
        apply.setOnClickListener(v -> saveAndRestart());
        layout.addView(apply);

        setContentView(layout);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(14); tv.setPadding(0, 16, 0, 4);
        return tv;
    }

    private void saveAndRestart() {
        String val = mode == 0 ? "none" : mode == 2 ? "dynamic" : "fixed";
        try {
            int br = Integer.parseInt(blurRadiusInput.getText().toString().trim());
            int ho = Integer.parseInt(heightOffsetInput.getText().toString().trim());
            int wo = Integer.parseInt(widthOffsetInput.getText().toString().trim());
            int co = Integer.parseInt(cornerInput.getText().toString().trim());
            boolean sq = squircleCheck.isChecked();

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            sp.edit().putString("light_mode", val)
                .putInt("blur_radius", br).putInt("height_offset", ho)
                .putInt("width_offset", wo).putInt("corner_offset", co)
                .putBoolean("squircle", sq).commit();

            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo '" + val + "' > /sdcard/dock_light.txt\n");
            os.writeBytes("echo '" + br + "' > /sdcard/dock_blur_radius.txt\n");
            os.writeBytes("echo '" + ho + "' > /sdcard/dock_height_offset.txt\n");
            os.writeBytes("echo '" + wo + "' > /sdcard/dock_width_offset.txt\n");
            os.writeBytes("echo '" + co + "' > /sdcard/dock_corner_offset.txt\n");
            os.writeBytes("echo '" + (sq ? "1" : "0") + "' > /sdcard/dock_squircle.txt\n");
            os.writeBytes("am force-stop com.miui.home\nsleep 1\n");
            os.writeBytes("am start -n com.miui.home/.launcher.Launcher\nexit\n");
            os.flush(); p.waitFor();
            Toast.makeText(this, "Done: " + val, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
