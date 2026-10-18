package fun.dunn.nasremote;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import android.text.InputType;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,report;
    private String page="home";
    private SharedPreferences config;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(status!=null) status.setText(KeyLog.connected ? (config.getBoolean("auto_enabled",true)
                ? "●  后台已就绪 · 切到 HDMI "+selected()+" 自动连接" : "自动连接已关闭") : "○  尚未授权 · 请进入更多设置");
            if(report!=null) report.setText((RemoteService.instance==null ? "后台服务未连接" : RemoteService.instance.bridgeStatus())
                +"\n\n"+(KeyLog.started==0 ? getSharedPreferences("diagnostic",MODE_PRIVATE).getString("last_report","暂无诊断记录") : KeyLog.report()));
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        config=getSharedPreferences("bridge",MODE_PRIVATE); home(false);
    }
    private int selected(){return config.getInt("selected_hdmi",3);}
    private int dp(float n){return SunnyTheme.dp(this,n);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(LinearLayout p,String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);p.addView(t);return t;
    }
    private void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private LinearLayout screen(String name,String title,String subtitle){
        page=name;status=null;report=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(SunnyTheme.BG);
        LinearLayout body=column();body.setPadding(dp(48),dp(28),dp(48),dp(28));scroll.addView(body);setContentView(scroll);
        text(body,"HDMI 遥控",15,SunnyTheme.BLUE).setTypeface(null,Typeface.BOLD);
        gap(body,20);text(body,title,30,SunnyTheme.INK).setTypeface(null,Typeface.BOLD);
        gap(body,8);text(body,subtitle,16,SunnyTheme.MUTED);gap(body,24);return body;
    }
    private Button button(LinearLayout p,String label,Runnable action){
        Button b=new Button(this);SunnyTheme.control(b);b.setText(label);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);p.addView(b,lp);return b;
    }
    private void home(boolean focusMore){
        LinearLayout p=screen("home","一只遥控器，就够了。","选择 NAS 所在的 HDMI 通道，切过去即可控制。");gap(p,12);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);p.addView(row);
        Button channel=new Button(this);SunnyTheme.control(channel);channel.setText("选择 HDMI 通道\n\nHDMI "+selected());channel.setTextSize(22);channel.setOnClickListener(v->channels());
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(142),1.4f);a.rightMargin=dp(20);row.addView(channel,a);
        Button more=new Button(this);SunnyTheme.control(more);more.setText("更多设置\n\n权限 · 连接 · 诊断");more.setTextSize(20);more.setOnClickListener(v->settings());row.addView(more,new LinearLayout.LayoutParams(0,dp(142),1));
        channel.setNextFocusRightId(more.getId());more.setNextFocusLeftId(channel.getId());
        gap(p,26);status=text(p,"",16,SunnyTheme.MUTED);gap(p,12);text(p,"切换输入源后自动接管 · 返回电视首页后停止",14,SunnyTheme.MUTED);
        (focusMore?more:channel).requestFocus();refreshOnce();
    }
    private void channels(){
        LinearLayout p=screen("channels","NAS 接在哪个通道？","HDMI 3 已实测。其他通道需在更多设置中校准输入源编号。");
        for(int i=1;i<=4;i++){final int n=i;Button b=button(p,"HDMI "+i+(i==selected()?"   ✓ 已选择":""),()->{
            config.edit().putInt("selected_hdmi",n).putBoolean("auto_enabled",true).apply();
            if(RemoteService.instance!=null)RemoteService.instance.targetChanged();home(false);toast("已选择 HDMI "+n+"，请切到该输入源");
        });if(i==selected())b.requestFocus();}button(p,"返回",()->home(false));
    }
    private void settings(){
        LinearLayout p=screen("settings","更多设置","日常使用无需填写 NAS 地址；首次使用请先开启后台权限。");
        status=text(p,"",16,SunnyTheme.MUTED);gap(p,16);
        button(p,"自动连接  ·  "+(config.getBoolean("auto_enabled",true)?"已开启":"已关闭"),()->{
            config.edit().putBoolean("auto_enabled",!config.getBoolean("auto_enabled",true)).apply();
            if(RemoteService.instance!=null)RemoteService.instance.stopBridge();settings();
        }).requestFocus();
        button(p,"无障碍与开机自启动",this::permissions);button(p,"连接与通道校准",this::connection);button(p,"按键诊断",this::diagnostics);
        button(p,"暂停本次转发",()->{if(RemoteService.instance!=null)RemoteService.instance.pauseAutomatic();toast("已暂停，切走再切回恢复");});
        button(p,"返回",()->home(true));refreshOnce();
    }
    private void permissions(){
        LinearLayout p=screen("permissions","后台权限","只需首次设置；后台就绪后，不必每次打开本应用。");
        text(p,"1. 启用本应用的无障碍服务。若电视没有入口，可按项目说明用 ADB 授权。\n2. 在电视权限管理中允许本应用自启动与关联启动。\n3. 切到所选 HDMI，后台自动发现并连接 NAS。",18,SunnyTheme.INK);gap(p,24);
        button(p,"打开无障碍设置",()->launch(Settings.ACTION_ACCESSIBILITY_SETTINGS)).requestFocus();
        button(p,"打开电视自启动管理",()->launch("com.tcl.guard.action.PermissionManager"));
        text(p,"此入口适用于已验证的雷鸟固件；其他电视请在系统设置中查找。",15,SunnyTheme.MUTED);gap(p,16);button(p,"返回",this::settings);
    }
    private void launch(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){toast("此固件没有该设置入口，请参照项目中的 ADB 配置说明");}}
    private EditText field(LinearLayout p,String label,String value,boolean secret,boolean number){
        text(p,label,16,SunnyTheme.MUTED);gap(p,6);EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(SunnyTheme.INK);e.setTextSize(18);
        e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:0));
        e.setText(value);e.setPadding(dp(16),dp(12),dp(16),dp(12));e.setBackground(SunnyTheme.shape(this,SunnyTheme.SURFACE,SunnyTheme.LINE,1));
        e.setOnFocusChangeListener((v,f)->e.setBackground(SunnyTheme.shape(this,SunnyTheme.SURFACE,f?SunnyTheme.BLUE:SunnyTheme.LINE,f?3:1)));
        p.addView(e,new LinearLayout.LayoutParams(-1,dp(56)));gap(p,16);return e;
    }
    private void connection(){
        LinearLayout p=screen("connection","连接与通道校准","自动发现优先。只有发现失败时才使用下面的备用地址与配对码。");
        EditText host=field(p,"NAS 地址（可留空）",config.getString("host",""),false,false);
        EditText port=field(p,"TCP 端口",config.getString("port","8765"),false,true);
        EditText token=field(p,"备用配对码（可留空）",config.getString("token",""),true,false);
        EditText source=field(p,"HDMI "+selected()+" 的输入源编号",String.valueOf(config.getInt("source_hdmi_"+selected(),selected()==3?10:-1)),false,false);
        text(p,"当前固件 HDMI 3 = 10。其他通道先选择、切过去，再返回此页查看最新通知并填写；未校准不会接管。",15,SunnyTheme.MUTED);
        String history=getSharedPreferences("source_probe",MODE_PRIVATE).getString("history","暂无输入源通知");text(p,history.substring(Math.max(0,history.length()-500)),13,SunnyTheme.MUTED);gap(p,16);
        button(p,"保存",()->{try{
            int n=Integer.parseInt(port.getText().toString().trim()),s=Integer.parseInt(source.getText().toString().trim());
            String h=host.getText().toString().trim(),t=token.getText().toString().replace("-","").trim().toLowerCase(java.util.Locale.ROOT);
            if(n<1||n>65535||s< -1||s>255||(!t.isEmpty()&&!t.matches("[0-9a-f]{32}"))||(h.isEmpty()!=t.isEmpty()))throw new IllegalArgumentException();
            config.edit().putString("host",h).putString("port",String.valueOf(n)).putString("token",t).putInt("source_hdmi_"+selected(),s).apply();
            if(RemoteService.instance!=null)RemoteService.instance.targetChanged();toast("已保存");settings();
        }catch(IllegalArgumentException e){toast("请检查端口、输入源编号，以及成对填写的地址和 32 位配对码");}});button(p,"返回",this::settings);
    }
    private void diagnostics(){
        LinearLayout p=screen("diagnostics","按键诊断","仅手动开始后记录 90 秒，最多 80 条；记录只保存在电视本地。");
        for(String scene:new String[]{"应用内","电视桌面","HDMI 画面"})button(p,"开始测试："+scene,()->{
            if(!KeyLog.connected){toast("请先开启无障碍服务");return;}KeyLog.start(this,scene);
            if("电视桌面".equals(scene))startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));else toast("诊断已开始");
        });
        button(p,"停止记录",()->{KeyLog.until=0;KeyLog.save(this);});button(p,"清除记录",()->{KeyLog.start(this,"已清除");KeyLog.until=0;KeyLog.save(this);});
        report=text(p,"",14,SunnyTheme.MUTED);gap(p,16);button(p,"返回",this::settings);refreshOnce();
    }
    private void refreshOnce(){handler.removeCallbacks(refresh);handler.post(refresh);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if("home".equals(page))super.onBackPressed();else if("channels".equals(page))home(false);else if("settings".equals(page))home(true);else settings();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){KeyLog.record(this,"ACTIVITY",event);return super.dispatchKeyEvent(event);}
    @Override protected void onResume(){super.onResume();RemoteService.foreground=true;if(RemoteService.instance!=null)RemoteService.instance.stopBridge();refreshOnce();}
    @Override protected void onPause(){RemoteService.foreground=false;handler.removeCallbacks(refresh);super.onPause();}
}
