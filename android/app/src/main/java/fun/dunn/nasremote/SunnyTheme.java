package fun.dunn.nasremote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;

/** Native adaptation of Sunny UI's blue palette and outline material. */
final class SunnyTheme {
    static final int BG=0xff111821, SURFACE=0xff1a2431, INK=0xffedf0f6, MUTED=0xffa7b1c0;
    static final int BLUE=0xff9dbbff, PALE=0xff263a56, LINE=0xff405675;
    static int dp(Context c, float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int fill,int edge,int width) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(c,12)); d.setStroke(dp(c,width),edge); return d;
    }
    static void control(Button b) {
        Context c=b.getContext(); StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},shape(c,0xff304b70,BLUE,3));
        s.addState(new int[]{android.R.attr.state_focused},shape(c,PALE,BLUE,3));
        s.addState(new int[]{},shape(c,SURFACE,LINE,1));
        b.setBackground(s); b.setTextColor(INK); b.setTextSize(18); b.setAllCaps(false);
        b.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,12)); b.setMinHeight(dp(c,58));
        b.setStateListAnimator(null); b.setElevation(dp(c,2)); b.setFocusable(true); b.setId(View.generateViewId());
    }
}
