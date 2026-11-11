package com.example.screenwake;

import androidx.appcompat.app.AppCompatActivity;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioAttributes;
import com.example.screenwake.ReaderService;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.*;
import android.widget.*;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.app.Activity;

import java.io.*;
import java.util.*;

public class VoiceReader extends Activity {
    static final int PICK_TREE=101, PICK_FILE=102, REQ_NOTIFY=103;
    TextView status,selected,engineInfo,gainLabel;
    EditText interval,webUrl;
    CheckBox monitorLocalBox,monitorWebBox;
    Spinner offlineVoice,onlineVoice,rate;
    TextView voiceStatus;
    SeekBar gainBar;

    String confirmedWebUrl="";
    Uri treeUri,fileUri;

    // 核心修改：把 TTS 变成静态全局变量，防止多页面冲突
    private static TextToSpeech globalTts = null;
    TextToSpeech tts;
    boolean ttsReady=false;

    final ArrayList<Voice> offlineVoices=new ArrayList<>();
    final ArrayList<Voice> onlineVoices=new ArrayList<>();
    String activeVoiceName="";
    boolean activeNetworkMode=false;

    final String[] rates={
        "0.5倍","0.75倍","1.0倍","1.25倍","1.5倍"
    };

    final float[] speedValues={
        0.5f,0.75f,1.0f,1.25f,1.5f
    };

    SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        prefs=getSharedPreferences("reader",0);

        activeVoiceName=prefs.getString("active_voice_name","");
        activeNetworkMode=prefs.getBoolean("active_network_mode",false);

        treeUri=readUri("tree");
        fileUri=readUri("file");

        buildUi();

        refreshSelection();

        confirmedWebUrl=prefs.getString("web_url","");

        webUrl.setText(confirmedWebUrl);

        requestNotificationPermission();

        initTTS();

        // 【日志】记录初始化
        LogManager.i(this, LogManager.SUB_APP, "VoiceReader 界面初始化完成 (页面类型: " + this.getClass().getSimpleName() + ")");
    }

    void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(22,20,22,24);
        scroll.addView(root);

        TextView title=new TextView(this);
        title.setText("文件智能朗读器");
        title.setTextSize(25);
        title.setPadding(0,0,0,14);
        root.addView(title);

        selected=new TextView(this);
        selected.setTextSize(14);
        root.addView(selected);

        addButton(root,"选择监控文件夹",v->chooseTree());
        addButton(root,"选择单个 TXT / CSV / LOG 文件",v->chooseFile());
        addButton(root,"朗读所选文件最后一行",v->readSelected());
        addButton(root,"朗读最新文件最后一行",v->readLatest());

        addDivider(root);

        TextView webLabel=new TextView(this);
        webLabel.setText("网页监控地址");
        root.addView(webLabel);

        webUrl=new EditText(this);
        webUrl.setSingleLine(true);
        webUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        webUrl.setHint("https://example.com/log.txt");
        root.addView(webUrl);

        addButton(root,"确定 / 保存网页地址",v->confirmWebUrl());

        monitorLocalBox=new CheckBox(this);
        monitorLocalBox.setText("监控本机文件夹最新 TXT / CSV / LOG");
        monitorLocalBox.setChecked(treeUri!=null);
        root.addView(monitorLocalBox);

        monitorWebBox=new CheckBox(this);
        monitorWebBox.setText("监控已确定的网页地址");
        root.addView(monitorWebBox);

        addDivider(root);

        TextView voiceTitle=new TextView(this);
        voiceTitle.setText("语音包选择（选择后必须点击对应确定按钮）");
        voiceTitle.setTextSize(19);
        root.addView(voiceTitle);

        TextView offlineLabel=new TextView(this);
        offlineLabel.setText("① 本机离线中文语音包（不需要网络）");
        root.addView(offlineLabel);

        offlineVoice=new Spinner(this);
        offlineVoice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item, new String[]{"正在读取本机离线语音包…"}));
        root.addView(offlineVoice);
        addButton(root,"确定使用本机离线语音包",v->confirmOfflineVoice());

        TextView onlineLabel=new TextView(this);
        onlineLabel.setText("② 本机联网中文语音包（需要网络）");
        root.addView(onlineLabel);

        onlineVoice=new Spinner(this);
        onlineVoice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item, new String[]{"正在读取本机联网语音包…"}));
        root.addView(onlineVoice);
        addButton(root,"确定使用本机联网语音包",v->confirmOnlineVoice());

        voiceStatus=new TextView(this);
        voiceStatus.setPadding(0,10,0,8);
        voiceStatus.setText("当前已确定：尚未选择语音包");
        root.addView(voiceStatus);

        engineInfo=new TextView(this);
        engineInfo.setPadding(0,6,0,8);
        engineInfo.setText("本机语音引擎：正在初始化");
        root.addView(engineInfo);

        addButton(root,"测试当前已确定的语音",v->testSpeech());
        addButton(root,"打开系统文字转语音设置（下载/安装语音包）",v->openTtsSettings());
        addButton(root,"重新读取语音包列表",v->{ if(ttsReady) loadChineseVoices(); else initTTS(); });

        addDivider(root);

        TextView rateLabel=new TextView(this);
        rateLabel.setText("朗读速度");
        root.addView(rateLabel);

        rate=new Spinner(this);
        rate.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rates));
        rate.setSelection(2);
        root.addView(rate);

        TextView gainTitle=new TextView(this);
        gainTitle.setText("朗读增强音量（50%～300%，默认200%）");
        root.addView(gainTitle);

        gainLabel=new TextView(this);
        root.addView(gainLabel);

        gainBar=new SeekBar(this);
        gainBar.setMax(250);
        gainBar.setProgress(150);
        root.addView(gainBar);
        gainLabel.setText("软件增益：200%");

        gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                int value=50+progress;
                gainLabel.setText("软件增益："+value+"%" + (value>=250?"（高增益可能失真）":""));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        TextView intervalLabel=new TextView(this);
        intervalLabel.setText("自动检查间隔（秒）");
        root.addView(intervalLabel);

        interval=new EditText(this);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText("10");
        root.addView(interval);

        addDivider(root);

        addButton(root,"开始自动监控并朗读",v->startMonitor());
        addButton(root,"暂停朗读",v->command(ReaderService.PAUSE));
        addButton(root,"继续朗读",v->command(ReaderService.RESUME));
        addButton(root,"停止朗读",v->command(ReaderService.STOP));

        status=new TextView(this);
        status.setText("正在准备");
        status.setTextSize(16);
        status.setPadding(0,18,0,0);
        root.addView(status);

        TextView note=new TextView(this);
        note.setText("说明：只有点击对应的确定按钮，语音包才会生效。测试朗读、手动朗读和定时监控统一使用当前已确定的语音包。本机离线语音不需要网络；本机联网语音需要网络和系统语音引擎支持。");
        root.addView(note);

        setContentView(scroll);
    }

    void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch(Exception e) {
            status.setText("无法打开系统 TTS 设置，请到系统设置中搜索‘文字转语音’");
            LogManager.e(this, LogManager.SUB_APP, "无法打开系统 TTS 设置界面", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 【日志】每次回到页面检查
        LogManager.i(this, LogManager.SUB_APP, "VoiceReader onResume，检查 TTS 状态。Ready=" + ttsReady);
        if(ttsReady && offlineVoice!=null && onlineVoice!=null) {
            loadChineseVoices();
        }
    }

    void addDivider(LinearLayout root) {
        View line=new View(this);
        line.setBackgroundColor(0xFFBBBBBB);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,1);
        p.setMargins(0,18,0,18);
        root.addView(line,p);
    }

    void addButton(LinearLayout root, String text, View.OnClickListener listener) {
        Button b=new Button(this);
        b.setText(text);
        root.addView(b);
        b.setOnClickListener(listener);
    }

    int selectedRatePosition() {
        int p=rate.getSelectedItemPosition();
        if(p<0 || p>=speedValues.length) return 2;
        return p;
    }

    void restoreVoiceSelection(Spinner spinner,ArrayList<Voice> list,String name) {
        if(spinner==null || list.isEmpty() || name==null || name.isEmpty()) return;
        for(int i=0;i<list.size();i++) {
            if(name.equals(list.get(i).getName())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    void confirmOfflineVoice() {
        int p=offlineVoice==null?-1:offlineVoice.getSelectedItemPosition();
        if(p<0 || p>=offlineVoices.size()) {
            status.setText("没有可确定的本机离线中文语音包。请先在手机系统 TTS 设置中下载离线中文语音。");
            LogManager.w(this, LogManager.SUB_APP, "确定离线语音包失败: 列表为空或未选择");
            return;
        }
        Voice v=offlineVoices.get(p);
        activeVoiceName=v.getName();
        activeNetworkMode=false;
        prefs.edit().putString("active_voice_name",activeVoiceName)
                .putString("offline_voice_name",activeVoiceName)
                .putBoolean("active_network_mode",false).apply();
        voiceStatus.setText("当前已确定：本机离线语音包\n"+v.getName());
        updateRunningServiceVoice();
        status.setText("已确定本机离线语音包，可以测试或开始监控");
        LogManager.i(this, LogManager.SUB_APP, "语音配置变更: 已切换本机离线语音包 [" + v.getName() + "]");
    }

    void confirmOnlineVoice() {
        int p=onlineVoice==null?-1:onlineVoice.getSelectedItemPosition();
        if(p<0 || p>=onlineVoices.size()) {
            status.setText("没有可确定的本机联网中文语音包。请检查系统 TTS 引擎及联网中文音色。");
            LogManager.w(this, LogManager.SUB_APP, "确定联网语音包失败: 列表为空或未选择");
            return;
        }
        Voice v=onlineVoices.get(p);
        activeVoiceName=v.getName();
        activeNetworkMode=true;
        prefs.edit().putString("active_voice_name",activeVoiceName)
                .putString("online_voice_name",activeVoiceName)
                .putBoolean("active_network_mode",true).apply();
        voiceStatus.setText("当前已确定：本机联网语音包\n"+v.getName());
        updateRunningServiceVoice();
        status.setText("已确定本机联网语音包，可以测试或开始监控");
        LogManager.i(this, LogManager.SUB_APP, "语音配置变更: 已切换本机联网语音包 [" + v.getName() + "]");
    }

    void updateRunningServiceVoice() {
        Intent i=new Intent(this,ReaderService.class);
        i.setAction(ReaderService.CONFIG);
        putSpeechSettings(i);
        startService(i);
    }

    void confirmWebUrl() {
        String url=webUrl.getText().toString().trim();
        if(!url.isEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
            status.setText("网址必须以 http:// 或 https:// 开头");
            LogManager.w(this, LogManager.SUB_NETWORK, "保存网页监控地址失败: 格式不正确 (" + url + ")");
            return;
        }
        confirmedWebUrl=url;
        prefs.edit().putString("web_url",confirmedWebUrl).apply();
        monitorWebBox.setChecked(!confirmedWebUrl.isEmpty());
        status.setText(confirmedWebUrl.isEmpty() ? "网页地址已清空" : "网页地址已确定并保存");
        if (confirmedWebUrl.isEmpty()) {
            LogManager.i(this, LogManager.SUB_NETWORK, "网页监控地址已清空");
        } else {
            LogManager.i(this, LogManager.SUB_NETWORK, "网页监控地址已更新保存: " + confirmedWebUrl);
        }
    }

    // ==========================================
    // 重点修改区域：TTS 初始化与硬件信息“放大”日志
    // ==========================================
    void initTTS() {
        status.setText("正在初始化系统语音引擎……");
        
        // 打印当前手机系统硬件信息（放大镜级别）
        LogManager.i(this, LogManager.SUB_APP, "==== 开始 TTS 初始化流程 ====");
        LogManager.i(this, LogManager.SUB_APP, "手机型号: " + Build.MANUFACTURER + " " + Build.MODEL + " | Android 版本: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        // 1. 检查全局实例是否已经存在且可用（解决子页面冲突的核心）
        if (globalTts != null) {
            LogManager.i(this, LogManager.SUB_APP, "检测到全局 TTS 实例已存在，尝试复用...");
            tts = globalTts;
            try {
                // 测试实例是否还存活
                tts.setLanguage(Locale.forLanguageTag("zh-CN"));
                ttsReady = true;
                String engine = tts.getDefaultEngine();
                engineInfo.setText("本机语音引擎(复用)：" + (engine == null ? "系统默认" : engine));
                LogManager.i(this, LogManager.SUB_APP, "复用 TTS 成功，当前绑定引擎: " + engine);
                loadChineseVoices();
                status.setText("本机中文语音已就绪");
                return; // 直接返回，不再新建
            } catch (Exception e) {
                LogManager.w(this, LogManager.SUB_APP, "复用旧 TTS 实例失败，准备重新创建。原因: " + e.getMessage());
                globalTts = null;
            }
        }

        // 2. 创建新实例（使用 getApplicationContext 避免 Activity 销毁导致引擎崩溃，但也推荐用 this）
        LogManager.i(this, LogManager.SUB_APP, "新建 TTS 实例，绑定 Context: " + this.getClass().getSimpleName());
        
        tts = new TextToSpeech(this, result -> {
            if(result != TextToSpeech.SUCCESS) {
                ttsReady = false;
                engineInfo.setText("本机语音引擎：初始化失败");
                status.setText("本机 TTS 初始化失败");
                
                // 极度放大的失败日志
                LogManager.e(this, LogManager.SUB_CRASH, "【致命错误】TTS 语音引擎初始化失败！系统返回码: " + result + "。可能原因：引擎未安装、被其他应用占用、或系统限制了子页面绑定。", null);
                return;
            }

            ttsReady = true;
            globalTts = tts; // 初始化成功后存入全局静态变量
            LogManager.i(this, LogManager.SUB_APP, "TTS onInit 回调成功！Result: SUCCESS");

            try {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                int langResult = tts.setLanguage(Locale.forLanguageTag("zh-CN"));
                LogManager.i(this, LogManager.SUB_APP, "设置中文语言返回码: " + langResult);

                String engine = tts.getDefaultEngine();
                LogManager.i(this, LogManager.SUB_APP, "系统当前默认 TTS 引擎: " + engine);

                // 打印所有安装的引擎
            if (Build.VERSION.SDK_INT >= 21) {
    List<TextToSpeech.EngineInfo> engines = tts.getEngines(); 
    if (engines != null) {
        LogManager.i(this, LogManager.SUB_APP, "手机内检测到 TTS 引擎数量: " + engines.size());
        for (TextToSpeech.EngineInfo info : engines) {
            LogManager.i(this, LogManager.SUB_APP, "  -> 发现引擎: " + info.name + " (Label: " + info.label + ")");
        }
         }
               }

                engineInfo.setText("本机语音引擎：" + (engine == null ? "系统默认" : engine));

                loadChineseVoices();
                status.setText("本机中文语音已就绪");

            } catch(Exception e) {
                status.setText("TTS 初始化异常：" + e.getMessage());
                LogManager.e(this, LogManager.SUB_CRASH, "TTS 初始化引发异常", e);
            }
        });
    }

    void loadChineseVoices() {
        offlineVoices.clear();
        onlineVoices.clear();
        
        LogManager.i(this, LogManager.SUB_APP, "==== 开始读取系统语音包 ====");
        try {
            if(Build.VERSION.SDK_INT>=21) {
                Set<Voice> all=tts.getVoices();
                if(all!=null) {
                    LogManager.i(this, LogManager.SUB_APP, "系统底层返回语音包总数: " + all.size());
                    for(Voice v:all) {
                        Locale l=v.getLocale();
                        if(l==null || !"zh".equalsIgnoreCase(l.getLanguage())) continue;
                        
                        if(v.isNetworkConnectionRequired()) {
                            onlineVoices.add(v);
                        } else {
                            offlineVoices.add(v);
                        }
                    }
                } else {
                    LogManager.w(this, LogManager.SUB_APP, "系统底层 getVoices() 返回了 NULL！");
                }
            } else {
                LogManager.w(this, LogManager.SUB_APP, "系统版本低于 API 21，无法获取详细 Voice 列表");
            }
        } catch(Exception e) {
            status.setText("读取系统语音包失败："+e.getMessage());
            LogManager.e(this, LogManager.SUB_APP, "读取系统语音包列表发生异常", e);
        }
        
        LogManager.i(this, LogManager.SUB_APP, "中文语音包筛选结果 -> 离线包: " + offlineVoices.size() + " 个 | 联网包: " + onlineVoices.size() + " 个");

        if(offlineVoices.isEmpty()) {
            LogManager.w(this, LogManager.SUB_APP, "警告：本机未检测到可用的中文离线语音包！");
        }
        
        Comparator<Voice> byQuality=(a,b)->Integer.compare(b.getQuality(),a.getQuality());
        Collections.sort(offlineVoices,byQuality);
        Collections.sort(onlineVoices,byQuality);
        
        setVoiceAdapter(offlineVoice,offlineVoices,"没有可用的离线中文语音包");
        setVoiceAdapter(onlineVoice,onlineVoices,"没有可用的联网中文语音包");
        restoreVoiceSelection(offlineVoice,offlineVoices,prefs.getString("offline_voice_name",""));
        restoreVoiceSelection(onlineVoice,onlineVoices,prefs.getString("online_voice_name",""));
        
        ArrayList<Voice> activeList=activeNetworkMode?onlineVoices:offlineVoices;
        boolean activeFound=false;
        for(Voice v:activeList) {
            if(activeVoiceName!=null && activeVoiceName.equals(v.getName())) {
                activeFound=true;
                break;
            }
        }
        if(!activeFound && activeVoiceName!=null && !activeVoiceName.isEmpty()) {
            activeVoiceName="";
            prefs.edit().remove("active_voice_name").apply();
            LogManager.w(this, LogManager.SUB_APP, "之前保存的语音包已失效，已重置选择");
        }
        
        restoreVoiceSelection(activeNetworkMode?onlineVoice:offlineVoice, activeList,activeVoiceName);
        if(activeVoiceName.isEmpty()) {
            voiceStatus.setText("当前已确定：尚未选择语音包，请点击其中一个确定按钮");
        } else {
            voiceStatus.setText("当前已确定："+(activeNetworkMode?"本机联网语音包":"本机离线语音包")+"\n"+activeVoiceName);
        }
        
        engineInfo.setText("本机语音引擎："+(tts.getDefaultEngine()==null?"系统默认":tts.getDefaultEngine())+
                "\n离线中文包："+offlineVoices.size()+" 个；联网中文包："+onlineVoices.size()+" 个");
    }

    void setVoiceAdapter(Spinner spinner,ArrayList<Voice> list,String emptyLabel) {
        ArrayList<String> labels=new ArrayList<>();
        for(Voice v:list) {
            Locale l=v.getLocale();
            labels.add((l==null?"中文":l.getDisplayName())+" · "+v.getName());
        }
        if(labels.isEmpty()) labels.add(emptyLabel);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,labels));
    }

    void testSpeech() {
        if(activeVoiceName==null || activeVoiceName.isEmpty()) {
            status.setText("请先选择语音包，并点击对应的确定按钮");
            LogManager.w(this, LogManager.SUB_COMMAND, "测试朗读失败: 尚未选择并确认语音包");
            return;
        }
        speakThroughService("你好，现在正在测试当前已确定的中文语音，一二三四五。");
    }

    void speakText(String text) {
        if(activeVoiceName==null || activeVoiceName.isEmpty()) {
            status.setText("请先选择语音包，并点击对应的确定按钮");
            LogManager.w(this, LogManager.SUB_COMMAND, "手动朗读失败: 尚未选择并确认语音包");
            return;
        }
        speakThroughService(text);
    }

    void speakThroughService(String text) {
        Intent i=new Intent(this,ReaderService.class);
        i.setAction(ReaderService.SPEAK);
        i.putExtra("text",text);
        putSpeechSettings(i);
        startReaderService(i);
        status.setText(activeNetworkMode?"已提交本机联网语音朗读":"已提交本机离线语音朗读");
        LogManager.i(this, LogManager.SUB_COMMAND, "提交语音播放请求: " + (activeNetworkMode?"[联网模式]":"[离线模式]") + " 文本长度:" + text.length());
    }

    void putSpeechSettings(Intent i) {
        i.putExtra("edge_mode",false);
        i.putExtra("network_voice",activeNetworkMode);
        i.putExtra("local_voice_name",activeVoiceName==null?"":activeVoiceName);
        i.putExtra("speed",speedValues[selectedRatePosition()]);
        i.putExtra("gain",(50+gainBar.getProgress())/100f);
    }

    void startReaderService(Intent i) {
        if(Build.VERSION.SDK_INT>=26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    void chooseTree() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }

    void chooseFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain","text/csv","application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_FILE);
    }

    @Override
    protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK || data==null || data.getData()==null) return;
        Uri u=data.getData();
        try {
            int f=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(u,f);
        } catch(Exception ignored) {}

        if(req==PICK_TREE) {
            treeUri=u;
            saveUri("tree",u);
            if(monitorLocalBox!=null) monitorLocalBox.setChecked(true);
            refreshSelection();
            status.setText("监控文件夹授权成功");
            LogManager.i(this, LogManager.SUB_UPLOAD, "监控文件夹授权成功: " + treeDisplayName(u));
        } else if(req==PICK_FILE) {
            fileUri=u;
            saveUri("file",u);
            refreshSelection();
            status.setText("文件已选择");
            LogManager.i(this, LogManager.SUB_UPLOAD, "指定监控文件成功: " + displayName(u));
        }
    }

    void refreshSelection() {
        selected.setText("监控目录："+(treeUri==null?"未选择":treeDisplayName(treeUri))+"\n指定文件："+(fileUri==null?"未选择":displayName(fileUri)));
    }

    String treeDisplayName(Uri tree) {
        try {
            String id=DocumentsContract.getTreeDocumentId(tree);
            Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);
            try(Cursor c=getContentResolver().query(doc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null,null,null)) {
                if(c!=null && c.moveToFirst()) return c.getString(0);
            }
        } catch(Exception ignored) {}
        return "已选择目录";
    }

    String displayName(Uri u) {
        try(Cursor c=getContentResolver().query(u,null,null,null,null)) {
            if(c!=null && c.moveToFirst()) {
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(n>=0) return c.getString(n);
            }
        } catch(Exception ignored) {}
        return "未知文件";
    }

    void saveUri(String k,Uri u) { prefs.edit().putString(k,u.toString()).apply(); }

    Uri readUri(String k) {
        String s=prefs==null?getSharedPreferences("reader",0).getString(k,null):prefs.getString(k,null);
        return s==null?null:Uri.parse(s);
    }

    void readSelected() {
        if(fileUri==null) {
            status.setText("请先选择一个文件");
            LogManager.w(this, LogManager.SUB_APP, "读取失败: 用户未选择指定的文件");
            return;
        }
        readUriLastLine(fileUri);
    }

    void readLatest() {
        if(treeUri==null) {
            status.setText("请先选择监控文件夹");
            LogManager.w(this, LogManager.SUB_APP, "读取失败: 用户未选择监控目录");
            return;
        }
        try {
            Uri u=latestTextFile(treeUri);
            if(u==null) {
                status.setText("目录中没有 TXT/CSV/LOG 文件");
                LogManager.w(this, LogManager.SUB_APP, "读取最新文件失败: 目录中没有符合条件的文本文件");
                return;
            }
            readUriLastLine(u);
        } catch(Exception e) {
            status.setText("读取目录失败："+e.getMessage());
            LogManager.e(this, LogManager.SUB_APP, "读取监控目录失败", e);
        }
    }

    Uri latestTextFile(Uri tree) throws Exception {
        String id=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        Uri best=null;
        long bestTime=-1;
        try(Cursor c=getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        }, null,null,null)) {
            if(c==null) return null;
            while(c.moveToNext()) {
                String n=c.getString(1);
                if(n==null) continue;
                String low=n.toLowerCase(Locale.ROOT);
                if(!(low.endsWith(".txt") || low.endsWith(".csv") || low.endsWith(".log"))) continue;
                long t=c.getLong(2);
                if(t>bestTime) {
                    bestTime=t;
                    best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));
                }
            }
        }
        return best;
    }

    String lastLine(Uri u) throws Exception {
        String last="";
        InputStream in=getContentResolver().openInputStream(u);
        if(in==null) return "";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"))) {
            String line;
            while((line=r.readLine())!=null) {
                if(!line.trim().isEmpty()) last=line.trim();
            }
        }
        return last;
    }

    void readUriLastLine(Uri u) {
        try {
            String s=lastLine(u);
            if(s.isEmpty()) {
                status.setText("文件最后一行为空");
                LogManager.w(this, LogManager.SUB_APP, "读取文件结束: 文件最后一行为空");
            } else {
                speakText(s);
            }
        } catch(Exception e) {
            status.setText("读取失败："+e.getMessage());
            LogManager.e(this, LogManager.SUB_APP, "读取文件内容发生异常", e);
        }
    }

    void startMonitor() {
        boolean useLocal=monitorLocalBox.isChecked();
        boolean useWeb=monitorWebBox.isChecked();
        String url=confirmedWebUrl;
        if(useLocal && treeUri==null) {
            status.setText("已勾选本机监控，但还没有选择文件夹");
            LogManager.w(this, LogManager.SUB_COMMAND, "启动监控失败: 未选择文件夹");
            return;
        }
        if(useWeb && url.isEmpty()) {
            status.setText("请先输入网页地址并保存");
            LogManager.w(this, LogManager.SUB_COMMAND, "启动监控失败: 未填写网页地址");
            return;
        }
        if(!useLocal && !useWeb) {
            status.setText("请至少勾选一种监控来源");
            LogManager.w(this, LogManager.SUB_COMMAND, "启动监控失败: 未勾选任何监控来源");
            return;
        }
        if(activeVoiceName==null || activeVoiceName.isEmpty()) {
            status.setText("请先选择本机离线或联网语音包，并点击对应的确定按钮");
            LogManager.w(this, LogManager.SUB_COMMAND, "启动监控失败: 未选择并确认语音包");
            return;
        }

        int sec=10;
        try {
            sec=Math.max(3, Integer.parseInt(interval.getText().toString().trim()));
        } catch(Exception ignored) {
            LogManager.w(this, LogManager.SUB_TIMER, "监控间隔解析失败，已使用默认 10 秒");
        }

        Intent i=new Intent(this,ReaderService.class);
        i.setAction(ReaderService.START);
        if(useLocal && treeUri!=null) i.putExtra("tree",treeUri.toString());
        i.putExtra("monitor_local",useLocal);
        i.putExtra("monitor_web",useWeb);
        i.putExtra("url",useWeb?url:"");
        i.putExtra("interval",sec);
        putSpeechSettings(i);
        startReaderService(i);
        status.setText("后台监控已启动："+sec+"秒");
        
        LogManager.i(this, LogManager.SUB_COMMAND, "启动后台监控指令. 本机监控:" + useLocal + " 网页监控:" + useWeb);
        LogManager.i(this, LogManager.SUB_TIMER, "自动监控定时器已启动，间隔: " + sec + "秒");
    }

    void command(String action) {
        Intent i=new Intent(this,ReaderService.class);
        i.setAction(action);
        startService(i);
        String actionName = action.equals(ReaderService.PAUSE) ? "暂停" : (action.equals(ReaderService.RESUME) ? "继续" : "停止");
        LogManager.i(this, LogManager.SUB_COMMAND, "下发控制指令: " + actionName + "朗读");
    }

    void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
            LogManager.i(this, LogManager.SUB_APP, "请求发送通知栏权限");
        }
    }

    @Override
    protected void onDestroy() {
        // 【极其重要】去掉 tts.shutdown()，防止关闭子页面导致主页面或后台服务语音崩溃
        if(tts != null && ttsReady) {
            tts.stop(); // 仅仅停止当前正在播放的语音
            LogManager.i(this, LogManager.SUB_APP, "VoiceReader 页面销毁，调用 tts.stop() 但保留引擎实例。");
        }
        super.onDestroy();
    }
    }
