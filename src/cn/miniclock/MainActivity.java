package cn.miniclock;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;
import android.view.View;

public class MainActivity extends Activity {
    private boolean awaitingPermission;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) awaitingPermission = state.getBoolean("awaiting");
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad * 2, pad, pad);
        page.setBackgroundColor(Color.rgb(248,250,249));
        TextView title = new TextView(this);
        title.setText("毫秒悬浮钟"); title.setTextSize(28); title.setTextColor(Color.rgb(20,45,39));
        page.addView(title);
        TextView info = new TextView(this);
        info.setText("时:分:秒.十分之一秒\n\n拖动时钟调整位置，点 × 关闭。\n时间跟随手机系统，建议开启自动设置时间。\n");
        info.setTextSize(16); info.setTextColor(Color.DKGRAY); page.addView(info);
        Button start = new Button(this); start.setText("开启悬浮钟"); page.addView(start);
        start.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { requestStart(); }});
        Button stop = new Button(this); stop.setText("关闭悬浮钟"); page.addView(stop);
        stop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            stopService(new Intent(MainActivity.this, ClockService.class));
            status.setText("已关闭");
        }});
        final SharedPreferences options = getSharedPreferences("options",0);
        final TextView sizeLabel = new TextView(this);
        int percent = Math.max(50,Math.min(200,options.getInt("size",100)));
        sizeLabel.setText("大小："+percent+"%"); sizeLabel.setTextSize(16); page.addView(sizeLabel);
        SeekBar size = new SeekBar(this); size.setMax(150); size.setProgress(percent-50);
        page.addView(size);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user) {
                int value=progress+50; sizeLabel.setText("大小："+value+"%");
                if(user) options.edit().putInt("size",value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        Switch transparent = new Switch(this); transparent.setText("透明背景"); transparent.setTextSize(16);
        transparent.setPadding(0,pad,0,pad); transparent.setChecked(options.getBoolean("transparent",false));
        page.addView(transparent);
        transparent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button,boolean checked) {
                options.edit().putBoolean("transparent",checked).apply();
            }
        });
        status = new TextView(this); page.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.addView(page); setContentView(scroll);
    }
    private void requestStart() {
        if (!Settings.canDrawOverlays(this)) {
            awaitingPermission = true;
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); }
            catch (Exception e) {
                awaitingPermission = false;
                Toast.makeText(this,"请在系统设置中允许本应用显示悬浮窗",Toast.LENGTH_LONG).show();
            }
            return;
        }
        Intent i = new Intent(this, ClockService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("已开启；可切换到抢票应用");
        } catch (Exception e) { status.setText("启动失败，请检查悬浮窗权限后重试"); }
    }
    @Override protected void onResume() {
        super.onResume();
        if (awaitingPermission) {
            awaitingPermission = false;
            if (Settings.canDrawOverlays(this)) requestStart();
            else status.setText("尚未获得悬浮窗权限，点击开启重试");
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaiting", awaitingPermission); super.onSaveInstanceState(state);
    }
}
