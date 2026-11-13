package com.example.screenwake;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.media.*;
import android.provider.DocumentsContract;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.database.Cursor;
import android.text.Html;

import android.text.Spanned;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

public class ReaderService extends Service implements TextToSpeech.OnInitListener {
    public static final String START="reader.START", PAUSE="reader.PAUSE", RESUME="reader.RESUME", STOP="reader.STOP";
    private static final int NOTIFY=71;
    private static final String CHANNEL="reader";
    private TextToSpeech tts;
    private Uri tree;
    public static final String SPEAK="reader.SPEAK";
    public static final String CONFIG="reader.CONFIG";
    private String webUrl="";
    private boolean monitorLocal=true, monitorWeb=false;
    private int interval=10;
    private float speed=1f;
    private volatile boolean running=false, paused=false, ready=false;
    private ScheduledExecutorService scheduler;
    private ExecutorService worker;
    private PowerManager.WakeLock wakeLock;
    private final Map<String,String> lastSpoken=new ConcurrentHashMap<>();

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts=new TextToSpeech(this,this);
        worker=Executors.newSingleThreadExecutor();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null) {
            wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VoiceReader:Monitor");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override public int onStartCommand(Intent i,int flags,int startId) {
        if(i==null) return START_NOT_STICKY;
        String a=i.getAction();
        if(START.equals(a)) {
            String raw=i.getStringExtra("tree"); tree=raw==null?null:Uri.parse(raw);
            monitorLocal=i.getBooleanExtra("monitor_local",raw!=null);
            monitorWeb=i.getBooleanExtra("monitor_web",i.getStringExtra("url")!=null && !i.getStringExtra("url").isEmpty());
            webUrl=i.getStringExtra("url"); if(webUrl==null) webUrl="";
            interval=Math.max(3,i.getIntExtra("interval",10));
            speed=i.getFloatExtra("speed",1f);
            running=true; paused=false;
            startForeground(NOTIFY,notification("正在启动监控"));
            try { if(wakeLock!=null && !wakeLock.isHeld()) wakeLock.acquire(); } catch(Exception ignored) {}
            beginSchedule();
        } else if(PAUSE.equals(a)) {
            paused=true; if(tts!=null) tts.stop(); update("已暂停");
        } else if(RESUME.equals(a)) {
            paused=false; update("继续监控"); submitCheck();
        } else if(STOP.equals(a)) stopServiceWork();
        return START_NOT_STICKY;
    }

    private void beginSchedule() {
        if(scheduler!=null) scheduler.shutdownNow();
        scheduler=Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::submitCheck,0,interval,TimeUnit.SECONDS);
    }
    private void submitCheck() {
        if(!running || paused || worker==null) return;
        worker.execute(() -> {
            if(!running || paused) return;
            if(monitorLocal && tree!=null) {
                try {
                    String[] item=getNewestLocalLine();
                    if(item!=null) speakIfChanged("local:"+item[0],item[1]);
                } catch(Exception e) { update("本机目录读取失败："+err(e)); }
            }
            if(monitorWeb && !webUrl.isEmpty() && running && !paused) {
                try {
                    String line=fetchWebLastLine(webUrl);
                    if(line!=null && !line.trim().isEmpty()) speakIfChanged("web:"+webUrl,line.trim());
                    else update("网页读取成功，但没有提取到文字");
                } catch(Exception e) { update("网页读取失败："+err(e)); }
            }
        });
    }

    private String[] getNewestLocalLine() throws Exception {
        String doc=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,doc);
        Uri best=null; String nameBest=""; long newest=-1;
        try(Cursor c=getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)) {
            if(c==null) return null;
            while(c.moveToNext()) {
                String n=c.getString(1); if(n==null) continue;
                String low=n.toLowerCase(Locale.ROOT);
                if(!(low.endsWith(".txt")||low.endsWith(".csv")||low.endsWith(".log"))) continue;
                long t=c.getLong(2);
                if(t>newest) { newest=t; nameBest=n; best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)); }
            }
        }
        if(best==null) return null;
        String line=readLocalLastLine(best);
        return line.isEmpty()?null:new String[]{nameBest,line};
    }

    private String readLocalLastLine(Uri uri) throws Exception {
        InputStream in=getContentResolver().openInputStream(uri);
        if(in==null) return "";
        String last="";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,Charset.forName("UTF-8")))) {
            String s; while((s=r.readLine())!=null) if(!s.trim().isEmpty()) last=s.trim();
        }
        return last;
    }

    // 先按文本直链读取；如果响应是 HTML，则抽取静态页面的可见文字。
    private String fetchWebLastLine(String address) throws Exception {
        HttpURLConnection c=null;
        try {
            URL url=new URL(address);
            URLConnection raw=url.openConnection();
            raw.setConnectTimeout(10000); raw.setReadTimeout(15000); raw.setUseCaches(false);
            raw.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 VoiceReader/1.0");
            raw.setRequestProperty("Accept","text/plain,text/html,text/csv,application/xhtml+xml,*/*");
            if(!(raw instanceof HttpURLConnection)) throw new IOException("只支持 HTTP/HTTPS");
            c=(HttpURLConnection)raw; c.setInstanceFollowRedirects(true);
            int code=c.getResponseCode();
            if(code<200 || code>=300) throw new IOException("HTTP "+code);
            String contentType=c.getContentType();
            String charsetName="UTF-8";
            if(contentType!=null) {
                String lower=contentType.toLowerCase(Locale.ROOT);
                int cp=lower.indexOf("charset=");
                if(cp>=0) charsetName=contentType.substring(cp+8).split("[; ]")[0].replace("\"","").trim();
            }
            String body=readAll(c.getInputStream(),Charset.forName(charsetName));
            boolean html=(contentType!=null && (contentType.toLowerCase(Locale.ROOT).contains("text/html") ||
                    contentType.toLowerCase(Locale.ROOT).contains("application/xhtml"))) ||
                    body.trim().matches("(?is)^<!doctype\\s+html.*") ||
                    body.trim().matches("(?is)^<html[\\s>].*");
            String text=html?htmlToVisibleText(body):body;
            return lastNonEmptyLine(text);
        } finally { if(c!=null) c.disconnect(); }
    }

    private String readAll(InputStream in,Charset charset) throws Exception {
        StringBuilder b=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,charset))) {
            char[] buf=new char[4096]; int n;
            while((n=r.read(buf))!=-1) {
                b.append(buf,0,n);
                if(b.length()>5*1024*1024) throw new IOException("网页内容超过5MB，已停止读取");
            }
        }
        return b.toString();
    }

    private String htmlToVisibleText(String html) {
        String cleaned=html.replaceAll("(?is)<(script|style|noscript|svg|head)\\b[^>]*>.*?</\\1\\s*>"," ")
                .replaceAll("(?is)<!--.*?-->"," ")
                .replaceAll("(?i)<br\\s*/?>","\\n")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6]|section|article|pre|blockquote)\\s*>","\\n")
                .replaceAll("(?i)<(p|div|li|tr|h[1-6]|section|article|pre|blockquote)\\b[^>]*>","\\n");
        Spanned sp=Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY);
        String text=sp.toString().replace('\u00a0',' ');
        text=text.replaceAll("[\\t\\x0B\\f\\r ]+"," ").replaceAll(" *\\n *","\\n");
        return text;
    }

    private String lastNonEmptyLine(String text) {
        if(text==null) return "";
        String last="";
        for(String line:text.split("\\R")) if(!line.trim().isEmpty()) last=line.trim();
        return last;
    }

    private void speakIfChanged(String source,String line) {
        if(!running || paused || !ready || tts==null || line==null || line.isEmpty()) return;
        if(line.equals(lastSpoken.get(source))) return;
        try {
            tts.setLanguage(Locale.forLanguageTag("zh-CN"));
            tts.setSpeechRate(speed);
            int result=tts.speak(line,TextToSpeech.QUEUE_ADD,null,"auto_"+System.currentTimeMillis());
            if(result==TextToSpeech.ERROR) update("TTS 调用失败");
            else { lastSpoken.put(source,line); update("发现新内容："+line); }
        } catch(Exception e) { update("TTS 异常："+err(e)); }
    }

    @Override public void onInit(int result) {
        ready=(result==TextToSpeech.SUCCESS);
        if(!ready) { update("后台 TTS 初始化失败"); return; }
        try {
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setLanguage(Locale.forLanguageTag("zh-CN"));
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) { update("正在朗读"); }
                @Override public void onDone(String id) { update("朗读完成"); }
                @Override public void onError(String id) { update("后台 TTS 播放失败"); }
                @Override public void onError(String id,int code) { update("后台 TTS 错误："+code); }
            });
            update("后台语音已就绪");
        } catch(Exception e) { update("后台 TTS 配置失败："+err(e)); }
    }

    private String err(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }

    private void stopServiceWork() {
        running=false;
        if(scheduler!=null) { scheduler.shutdownNow(); scheduler=null; }
        if(tts!=null) tts.stop();
        try { if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release(); } catch(Exception ignored) {}
        stopForeground(true); stopSelf();
    }

    private void createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel ch=new NotificationChannel(CHANNEL,"文件朗读监控",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    private Notification notification(String msg) {
        Intent p=new Intent(this,ReaderService.class); p.setAction(PAUSE);
        PendingIntent pi=PendingIntent.getService(this,1,p,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("文件智能朗读器").setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"暂停",pi).build())
                .setOngoing(true).build();
    }
    private void update(String msg) {
        try {
            if(running) {
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                if(nm!=null) nm.notify(NOTIFY,notification(msg));
            }
        } catch(Exception ignored) {}
    }

    @Override public void onDestroy() {
        running=false;
        if(scheduler!=null) scheduler.shutdownNow();
        if(worker!=null) worker.shutdownNow();
        try { if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release(); } catch(Exception ignored) {}
        if(tts!=null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
