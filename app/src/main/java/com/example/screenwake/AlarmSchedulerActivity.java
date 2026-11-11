package com.example.screenwake;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmSchedulerActivity extends Activity {
    private static final int BG = Color.rgb(243, 246, 250);
    private static final int TEXT = Color.rgb(34, 34, 34);

    private EditText etMinute;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("⏰ 定时任务 (AlarmScheduler)");
        title.setTextSize(20);
        title.setTextColor(TEXT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView status = new TextView(this);
        refreshStatusText(status);
        status.setTextColor(TEXT);
        status.setTextSize(15);
        status.setPadding(0, dp(24), 0, dp(12));
        root.addView(status);

        // 间隔输入行
        TextView tipMin = new TextView(this);
        tipMin.setText("执行间隔（分钟，最小1）：");
        tipMin.setTextColor(TEXT);
        tipMin.setTextSize(15);
        tipMin.setPadding(0, dp(8),0,dp(8));
        root.addView(tipMin);

        etMinute = new EditText(this);
        etMinute.setTextColor(TEXT);
        etMinute.setBackground(roundedBg(Color.WHITE, dp(8)));
        etMinute.setPadding(dp(12),dp(12),dp(12),dp(12));
        etMinute.setHint("例如 30 代表30分钟");
        // 限制只能输入数字
        etMinute.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for(int i=start;i<end;i++){
                    if(!Character.isDigit(source.charAt(i))) return "";
                }
                return null;
            }
        }});
        // 读取当前已保存的间隔回填到输入框
        long curMin = ConfigManager.getIntervalMinutes(this);
        etMinute.setText(String.valueOf(curMin));

        root.addView(etMinute, new LinearLayout.LayoutParams(-1, dp(48)));

        Button btnSetInterval = new Button(this);
        btnSetInterval.setText("💾 保存间隔并启用定时");
        btnSetInterval.setAllCaps(false);
        btnSetInterval.setBackground(roundedBg(Color.rgb(80,160,255), dp(8)));
        btnSetInterval.setOnClickListener(v -> {
            String text = etMinute.getText().toString().trim();
            if(text.isEmpty()){
                Toast.makeText(this,"请输入分钟数",Toast.LENGTH_SHORT).show();
                return;
            }
            long min;
            try{
                min = Long.parseLong(text);
            }catch (Exception e){
                Toast.makeText(this,"数字格式错误",Toast.LENGTH_SHORT).show();
                return;
            }
            if(min < 1){
                Toast.makeText(this,"最小为1分钟",Toast.LENGTH_SHORT).show();
                return;
            }
            // 保存间隔，开启定时器
            ConfigManager.setIntervalMinutes(this, min);
            AlarmScheduler.enable(this);
            refreshStatusText(status);
            Toast.makeText(this,"已设置 "+min+" 分钟，定时已开启",Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpSet = new LinearLayout.LayoutParams(-1, dp(50));
        lpSet.topMargin = dp(12);
        root.addView(btnSetInterval, lpSet);

        Button trigger = new Button(this);
        trigger.setText("⚡ 立即触发一次");
        trigger.setAllCaps(false);
        trigger.setBackground(roundedBg(Color.rgb(255, 159, 67), dp(8)));
        trigger.setOnClickListener(v -> {
            AlarmScheduler.triggerNow(this);
            Toast.makeText(this, "已触发", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpTrigger = new LinearLayout.LayoutParams(-1, dp(50));
        lpTrigger.topMargin = dp(12);
        root.addView(trigger, lpTrigger);

        Button cancel = new Button(this);
        cancel.setText("🚫 取消定时任务");
        cancel.setAllCaps(false);
        cancel.setBackground(roundedBg(Color.rgb(255, 107, 129), dp(8)));
        cancel.setOnClickListener(v -> {
            AlarmScheduler.cancel(this);
            Toast.makeText(this, "定时已关闭", Toast.LENGTH_SHORT).show();
            refreshStatusText(status);
            finish();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.topMargin = dp(12);
        root.addView(cancel, p);

        setContentView(root);
    }

    private void refreshStatusText(TextView tv){
        boolean enable = ConfigManager.isEnabled(this);
        long min = ConfigManager.getIntervalMinutes(this);
        tv.setText("当前定时状态：" + (enable ? "✅已开启" : "❌已关闭") + "\n设置间隔："+min+" 分钟");
    }

    private GradientDrawable roundedBg(int c, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(r);
        return d;
    }
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
