package cn.miniclock;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.provider.Settings;
import android.graphics.*;
import android.view.*;
import java.util.TimeZone;

public class ClockService extends Service {
    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private ClockView clock;
    private boolean registered;
    private SharedPreferences options;
    private float scale=1f;
    private boolean transparent;
    private final SharedPreferences.OnSharedPreferenceChangeListener optionListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
            readOptions(); clamp();
            if(clock!=null && clock.isAttachedToWindow()) {
                manager.updateViewLayout(clock,params); clock.invalidate();
            }
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (clock != null) {
                clock.active = !Intent.ACTION_SCREEN_OFF.equals(i.getAction());
                if (clock.active) clock.invalidate();
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel("clock", "悬浮时钟", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false); nm.createNotificationChannel(channel);
            }
            PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Intent stopIntent = new Intent(this,ClockService.class).setAction("stop");
            PendingIntent stop = PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this,"clock") : new Notification.Builder(this);
            b.setSmallIcon(cn.miniclock.R.drawable.clock).setContentTitle("毫秒悬浮钟正在显示")
                .setContentText("拖动调整位置，点悬浮条 × 关闭")
                .setContentIntent(open).setOngoing(true).addAction(0,"关闭",stop);
            if (Build.VERSION.SDK_INT >= 34) startForeground(1,b.build(),0x40000000);
            else startForeground(1,b.build());
            manager = (WindowManager)getSystemService(WINDOW_SERVICE);
            options=getSharedPreferences("options",0); readOptions();
            options.registerOnSharedPreferenceChangeListener(optionListener);
            int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params = new WindowManager.LayoutParams(dp(244),dp(48),type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP|Gravity.LEFT;
            params.x=getSharedPreferences("position",0).getInt("x",dp(12));
            params.y=getSharedPreferences("position",0).getInt("y",dp(100));
            params.setTitle("毫秒悬浮钟"); clamp();
            clock = new ClockView();
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            clock.active = pm.isInteractive();
            manager.addView(clock,params);
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen,filter);
            registered = true;
        } catch (Exception e) { stopSelf(); }
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void readOptions() {
        scale=Math.max(50,Math.min(200,options.getInt("size",100)))/100f;
        transparent=options.getBoolean("transparent",false);
    }
    private void clamp() {
        if (manager == null || params == null) return;
        Point size = new Point(); manager.getDefaultDisplay().getSize(size);
        params.width = Math.min(dp(164*scale+40),size.x);
        params.height = dp(Math.max(40,48*scale));
        params.x = Math.max(0,Math.min(params.x,size.x-params.width));
        params.y = Math.max(0,Math.min(params.y,size.y-params.height-dp(24)));
    }
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c); clamp();
        if(clock!=null && clock.isAttachedToWindow()) manager.updateViewLayout(clock,params);
    }
    @Override public int onStartCommand(Intent i,int flags,int id) {
        if(i!=null && "stop".equals(i.getAction())) stopSelf();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if(options!=null) options.unregisterOnSharedPreferenceChangeListener(optionListener);
        if(registered) unregisterReceiver(screen);
        if(clock!=null) {
            clock.active=false;
            if(clock.isAttachedToWindow()) manager.removeView(clock);
        }
        stopForeground(true); super.onDestroy();
    }
    private final class ClockView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final char[] digits = "00:00:00.0".toCharArray();
        private boolean active=true, moved;
        private float downX,downY;
        private int oldX,oldY;
        private final int slop = ViewConfiguration.get(ClockService.this).getScaledTouchSlop();
        ClockView() { super(ClockService.this); setContentDescription("悬浮时钟，拖动移动，右侧叉号关闭"); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        private void two(int index,int value) { digits[index]=(char)('0'+value/10); digits[index+1]=(char)('0'+value%10); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now=System.currentTimeMillis();
            long local=now+TimeZone.getDefault().getOffset(now);
            int day=(int)(((local%86400000L)+86400000L)%86400000L);
            two(0,day/3600000); two(3,day/60000%60); two(6,day/1000%60);
            digits[9]=(char)('0'+day%1000/100);
            paint.setStyle(Paint.Style.FILL);
            if(!transparent) {
                paint.setColor(Color.argb(235,19,25,29));
                canvas.drawRoundRect(0,0,getWidth(),getHeight(),dp(12),dp(12),paint);
            }
            paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(dp(23*scale));
            float padding=dp(12*scale);
            float available=getWidth()-dp(40)-padding;
            float width=paint.measureText(digits,0,digits.length);
            if(width>available) paint.setTextSize(paint.getTextSize()*available/width);
            float baseline=(getHeight()-paint.ascent()-paint.descent())/2;
            if(transparent) {
                paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
                canvas.drawText(digits,0,digits.length,padding,baseline,paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(Color.rgb(163,255,207));
            canvas.drawText(digits,0,digits.length,padding,baseline,paint);
            paint.setColor(Color.LTGRAY); paint.setStrokeWidth(dp(1.7f));
            float cx=getWidth()-dp(20), cy=getHeight()/2f, r=dp(5);
            if(transparent) {
                paint.setColor(Color.BLACK); paint.setStrokeWidth(dp(3.7f));
                canvas.drawLine(cx-r,cy-r,cx+r,cy+r,paint); canvas.drawLine(cx+r,cy-r,cx-r,cy+r,paint);
                paint.setColor(Color.LTGRAY); paint.setStrokeWidth(dp(1.7f));
            }
            canvas.drawLine(cx-r,cy-r,cx+r,cy+r,paint); canvas.drawLine(cx+r,cy-r,cx-r,cy+r,paint);
            if(active) postInvalidateDelayed(100-now%100);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX=e.getRawX(); downY=e.getRawY(); oldX=params.x; oldY=params.y; moved=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                    if(dx*dx+dy*dy>slop*slop) moved=true;
                    if(moved) {
                        params.x=oldX+Math.round(dx); params.y=oldY+Math.round(dy); clamp();
                        if(isAttachedToWindow()) manager.updateViewLayout(this,params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved && e.getX()>=getWidth()-dp(40)) { performClick(); stopSelf(); }
                    else if(moved) getSharedPreferences("position",0).edit().putInt("x",params.x).putInt("y",params.y).apply();
                    return true;
                case MotionEvent.ACTION_CANCEL: return true;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
