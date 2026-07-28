package com.kiminonawa.betterdock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataOutputStream;

public class SettingsActivity extends Activity {

    private int mode = 1;
    private EditText blurRadiusInput, heightOffsetInput, widthOffsetInput, cornerInput;
    private EditText sqStrokeWidthInput, sqStrokeOffInput, sqOuterCpInput;
    private LinearLayout sqGroup;
    private int blurRadius = 100, heightOffset, widthOffset, cornerOffset = -1;
    private int sqStrokeWidth = 4, sqStrokeOff = 8, sqOuterCp = 58; // ×100

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
        sqStrokeWidth = sp.getInt("sq_stroke_w", 4);
        sqStrokeOff = sp.getInt("sq_stroke_off", 8);
        sqOuterCp = sp.getInt("sq_outer_cp", 58);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 96, 48, 48);
        layout.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        TextView title = new TextView(this);
        title.setText("BetterDock");
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

        CheckBox sq = new CheckBox(this);
        sq.setText("Squircle Corners");
        sq.setChecked(sp.getBoolean("squircle", false));

        // Squircle-only group (shown/hidden with checkbox)
        sqGroup = new LinearLayout(this);
        sqGroup.setOrientation(LinearLayout.VERTICAL);

        sqGroup.addView(label("Stroke Width (px)"));
        sqStrokeWidthInput = new EditText(this);
        sqStrokeWidthInput.setText(String.valueOf(sqStrokeWidth));
        sqStrokeWidthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sqGroup.addView(sqStrokeWidthInput);

        sqGroup.addView(label("Stroke Offset (px)"));
        sqStrokeOffInput = new EditText(this);
        sqStrokeOffInput.setText(String.valueOf(sqStrokeOff));
        sqStrokeOffInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sqGroup.addView(sqStrokeOffInput);

        sqGroup.addView(label("Outer CP (×100, 55-65)"));
        sqOuterCpInput = new EditText(this);
        sqOuterCpInput.setText(String.valueOf(sqOuterCp));
        sqOuterCpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sqGroup.addView(sqOuterCpInput);

        sqGroup.setVisibility(sq.isChecked() ? View.VISIBLE : View.GONE);

        sq.setOnCheckedChangeListener((cb, checked) ->
            sqGroup.setVisibility(checked ? View.VISIBLE : View.GONE));

        layout.addView(sq);
        layout.addView(sqGroup);

        // Presets
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);

        Button miBtn = new Button(this);
        miBtn.setText("Xiaomi Default");
        miBtn.setOnClickListener(v -> {
            mode = 1; blurRadiusInput.setText("100"); heightOffsetInput.setText("0");
            widthOffsetInput.setText("0"); cornerInput.setText("-1"); sq.setChecked(false);
        });
        presetRow.addView(miBtn);

        Button ipadBtn = new Button(this);
        ipadBtn.setText("iPad Default");
        ipadBtn.setOnClickListener(v -> {
            mode = 1; blurRadiusInput.setText("120"); heightOffsetInput.setText("1");
            widthOffsetInput.setText("1"); cornerInput.setText("0"); sq.setChecked(true);
            sqStrokeWidthInput.setText("4"); sqStrokeOffInput.setText("8"); sqOuterCpInput.setText("58");
        });
        presetRow.addView(ipadBtn);
        layout.addView(presetRow);

        Button apply = new Button(this);
        apply.setText("Apply & Restart Launcher");
        apply.setOnClickListener(v -> {
            String val = mode == 0 ? "none" : mode == 2 ? "dynamic" : "fixed";
            try {
                int br = Integer.parseInt(blurRadiusInput.getText().toString().trim());
                int ho = Integer.parseInt(heightOffsetInput.getText().toString().trim());
                int wo = Integer.parseInt(widthOffsetInput.getText().toString().trim());
                int co = Integer.parseInt(cornerInput.getText().toString().trim());
                boolean squircle = sq.isChecked();
                int sw = Integer.parseInt(sqStrokeWidthInput.getText().toString().trim());
                int so = Integer.parseInt(sqStrokeOffInput.getText().toString().trim());
                int cp = Integer.parseInt(sqOuterCpInput.getText().toString().trim());

                sp.edit().putString("light_mode", val)
                    .putInt("blur_radius", br).putInt("height_offset", ho)
                    .putInt("width_offset", wo).putInt("corner_offset", co)
                    .putBoolean("squircle", squircle)
                    .putInt("sq_stroke_w", sw).putInt("sq_stroke_off", so)
                    .putInt("sq_outer_cp", cp).commit();

                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("echo '" + val + "' > /sdcard/dock_light.txt\n");
                os.writeBytes("echo '" + br + "' > /sdcard/dock_blur_radius.txt\n");
                os.writeBytes("echo '" + ho + "' > /sdcard/dock_height_offset.txt\n");
                os.writeBytes("echo '" + wo + "' > /sdcard/dock_width_offset.txt\n");
                os.writeBytes("echo '" + co + "' > /sdcard/dock_corner_offset.txt\n");
                os.writeBytes("echo '" + (squircle ? "1" : "0") + "' > /sdcard/dock_squircle.txt\n");
                os.writeBytes("echo '" + sw + "' > /sdcard/dock_sq_stroke_w.txt\n");
                os.writeBytes("echo '" + so + "' > /sdcard/dock_sq_stroke_off.txt\n");
                os.writeBytes("echo '" + cp + "' > /sdcard/dock_sq_outer_cp.txt\n");
                os.writeBytes("am force-stop com.miui.home\nsleep 1\n");
                os.writeBytes("am start -n com.miui.home/.launcher.Launcher\nexit\n");
                os.flush(); p.waitFor();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(apply);

        setContentView(scroll);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(14); tv.setPadding(0, 16, 0, 4);
        return tv;
    }
}
