package com.example.voicereader;

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

    public static final String START="reader.START";
    public static final String PAUSE="reader.PAUSE";
    public static final String RESUME="reader.RESUME";
    public static final String STOP="reader.STOP";
    public static final String SPEAK="reader.SPEAK";
    public static final String CONFIG="reader.CONFIG";

    private static final int NOTIFY=71;
    private static final String CHANNEL="reader";

    private TextToSpeech tts;
    private Uri tree;
    private String webUrl="";
    private String serverUrl="";
    private String apiKey="";
    private String edgeVoice="zh-CN-XiaoxiaoNeural";
    private String localVoiceName="";

    private boolean monitorLocal=true;
    private boolean monitorWeb=false;
    private boolean edgeMode=false;
    private boolean networkVoiceMode=false;

    private int interval=10;
    private float speed=1f;
    private float gain=2f;

    private Voice selectedNetworkVoice;

    private volatile boolean running=false;
    private volatile boolean paused=false;
    private volatile boolean ready=false;

    private ScheduledExecutorService scheduler;
    private ExecutorService worker;

    private PowerManager.WakeLock wakeLock;

    private AudioTrack activeTrack;

    private final Object audioLock=new Object();

    private final Map<String,String> lastSpoken=
            new ConcurrentHashMap<>();
    private final Queue<String> pendingTexts=new ConcurrentLinkedQueue<>();

    @Override
    public void onCreate() {
        super.onCreate();

        createChannel();

        worker=Executors.newSingleThreadExecutor();

        tts=new TextToSpeech(this,this);

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);

        if(pm!=null) {
            wakeLock=pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VoiceReader:Monitor"
            );
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent i,int flags,int startId) {

        if(i==null) return START_NOT_STICKY;

        String action=i.getAction();

        if(START.equals(action)) {

            String raw=i.getStringExtra("tree");
            tree=raw==null?null:Uri.parse(raw);

            monitorLocal=i.getBooleanExtra("monitor_local",raw!=null);
            monitorWeb=i.getBooleanExtra("monitor_web",false);

            webUrl=i.getStringExtra("url");
            if(webUrl==null) webUrl="";

            serverUrl=i.getStringExtra("server_url");
            if(serverUrl==null) serverUrl="";

            apiKey=i.getStringExtra("api_key");
            if(apiKey==null) apiKey="";

            edgeVoice=i.getStringExtra("edge_voice");
            if(edgeVoice==null) edgeVoice="zh-CN-XiaoxiaoNeural";

            edgeMode=false;
            networkVoiceMode=i.getBooleanExtra("network_voice",false);
            localVoiceName=i.getStringExtra("local_voice_name");
            if(localVoiceName==null) localVoiceName="";

            interval=Math.max(3,i.getIntExtra("interval",10));

            speed=Math.max(
                    0.5f,
                    Math.min(1.5f,i.getFloatExtra("speed",1f))
            );

            gain=Math.max(
                    0.5f,
                    Math.min(3f,i.getFloatExtra("gain",2f))
            );


            running=true;
            paused=false;

            startForeground(NOTIFY,notification("正在启动监控"));

            try {
                if(wakeLock!=null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
            } catch(Exception ignored) {}

            beginSchedule();

        } else if(CONFIG.equals(action)) {

            edgeMode=false;
            networkVoiceMode=i.getBooleanExtra("network_voice",false);
            localVoiceName=i.getStringExtra("local_voice_name");
            if(localVoiceName==null) localVoiceName="";
            speed=Math.max(0.5f,Math.min(1.5f,i.getFloatExtra("speed",1f)));
            gain=Math.max(0.5f,Math.min(3f,i.getFloatExtra("gain",2f)));
            if(running) update(networkVoiceMode?"已切换为本机联网语音包":"已切换为本机离线语音包");
            else stopSelfResult(startId);

        } else if(PAUSE.equals(action)) {

            paused=true;

            if(tts!=null) tts.stop();

            synchronized(audioLock) {
                if(activeTrack!=null) {
                    try {
                        activeTrack.pause();
                    } catch(Exception ignored) {}
                }
            }

            update("已暂停");

        } else if(RESUME.equals(action)) {

            paused=false;

            synchronized(audioLock) {
                if(activeTrack!=null) {
                    try {
                        activeTrack.play();
                    } catch(Exception ignored) {}
                }
            }

            update("继续监控");
            if(worker!=null) worker.execute(()->drainPendingTexts());
            submitCheck();

        } else if(STOP.equals(action)) {

            stopServiceWork();

        } else if(SPEAK.equals(action)) {

            edgeMode=false;
            networkVoiceMode=i.getBooleanExtra("network_voice",false);
            localVoiceName=i.getStringExtra("local_voice_name");
            if(localVoiceName==null) localVoiceName="";
            speed=Math.max(0.5f,Math.min(1.5f,i.getFloatExtra("speed",1f)));
            gain=Math.max(0.5f,Math.min(3f,i.getFloatExtra("gain",2f)));

            String text=i.getStringExtra("text");
            if(text!=null && !text.trim().isEmpty()) {
                if(!running) {
                    running=true;
                    paused=false;
                    startForeground(NOTIFY,notification("正在准备朗读"));
                }
                String content=text.trim();
                if(ready && worker!=null) {
                    worker.execute(()->speak(content));
                } else {
                    pendingTexts.offer(content);
                    update("语音引擎正在初始化，测试文字已排队等待播放");
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void drainPendingTexts() {
        if(!ready || !running || paused || worker==null) return;
        String queued;
        while(ready && running && !paused && (queued=pendingTexts.poll())!=null) {
            speak(queued);
        }
    }

    private void beginSchedule() {

        if(scheduler!=null) scheduler.shutdownNow();

        scheduler=Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(
                this::submitCheck,
                0,
                interval,
                TimeUnit.SECONDS
        );
    }

    private void submitCheck() {

        if(!running || paused || worker==null) return;

        worker.execute(()->{

            if(!running || paused) return;

            if(monitorLocal && tree!=null) {
                try {
                    String[] item=getNewestLocalLine();

                    if(item!=null) {
                        speakIfChanged("local:"+item[0],item[1]);
                    }

                } catch(Exception e) {
                    update("本机目录读取失败："+err(e));
                }
            }

            if(monitorWeb && !webUrl.isEmpty() && running && !paused) {

                try {
                    String line=fetchWebLastLine(webUrl);

                    if(line!=null && !line.trim().isEmpty()) {
                        speakIfChanged("web:"+webUrl,line.trim());
                    } else {
                        update("网页读取成功，但没有提取到文字");
                    }

                } catch(Exception e) {
                    update("网页读取失败："+err(e));
                }
            }
        });
    }

    private String[] getNewestLocalLine() throws Exception {

        String doc=DocumentsContract.getTreeDocumentId(tree);

        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,doc);

        Uri best=null;
        String nameBest="";
        long newest=-1;

        try(Cursor c=getContentResolver().query(
                children,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                },
                null,null,null
        )) {

            if(c==null) return null;

            while(c.moveToNext()) {

                String n=c.getString(1);
                if(n==null) continue;

                String low=n.toLowerCase(Locale.ROOT);

                if(!(low.endsWith(".txt") ||
                        low.endsWith(".csv") ||
                        low.endsWith(".log"))) continue;

                long t=c.getLong(2);

                if(t>newest) {
                    newest=t;
                    nameBest=n;

                    best=DocumentsContract.buildDocumentUriUsingTree(
                            tree,c.getString(0)
                    );
                }
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

        try(BufferedReader r=new BufferedReader(
                new InputStreamReader(in,Charset.forName("UTF-8"))
        )) {

            String s;

            while((s=r.readLine())!=null) {
                if(!s.trim().isEmpty()) last=s.trim();
            }
        }

        return last;
    }

    private String fetchWebLastLine(String address) throws Exception {

        HttpURLConnection c=null;

        try {

            URL url=new URL(address);

            URLConnection raw=url.openConnection();

            raw.setConnectTimeout(10000);
            raw.setReadTimeout(15000);
            raw.setUseCaches(false);

            raw.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 VoiceReader/2.0"
            );

            raw.setRequestProperty(
                    "Accept",
                    "text/plain,text/html,text/csv,*/*"
            );

            if(!(raw instanceof HttpURLConnection)) {
                throw new IOException("只支持 HTTP/HTTPS");
            }

            c=(HttpURLConnection)raw;
            c.setInstanceFollowRedirects(true);

            int code=c.getResponseCode();

            if(code<200 || code>=300) {
                throw new IOException("HTTP "+code);
            }

            String contentType=c.getContentType();

            String charsetName="UTF-8";

            if(contentType!=null) {

                String lower=contentType.toLowerCase(Locale.ROOT);

                int cp=lower.indexOf("charset=");

                if(cp>=0) {
                    charsetName=contentType.substring(cp+8)
                            .split("[; ]")[0]
                            .replace("\"","")
                            .trim();
                }
            }

            String body=readAll(
                    c.getInputStream(),
                    Charset.forName(charsetName)
            );

            boolean html=contentType!=null &&
                    (contentType.toLowerCase(Locale.ROOT).contains("text/html") ||
                     contentType.toLowerCase(Locale.ROOT).contains("application/xhtml"));

            String text=html?htmlToVisibleText(body):body;

            return lastNonEmptyLine(text);

        } finally {
            if(c!=null) c.disconnect();
        }
    }

    private String readAll(InputStream in,Charset charset) throws Exception {

        StringBuilder b=new StringBuilder();

        try(BufferedReader r=new BufferedReader(
                new InputStreamReader(in,charset)
        )) {

            char[] buf=new char[4096];

            int n;

            while((n=r.read(buf))!=-1) {

                b.append(buf,0,n);

                if(b.length()>5*1024*1024) {
                    throw new IOException("网页内容超过5MB");
                }
            }
        }

        return b.toString();
    }

    private String htmlToVisibleText(String html) {

        String cleaned=html
                .replaceAll("(?is)<(script|style|noscript|svg|head)\\b[^>]*>.*?</\\1\\s*>"," ")
                .replaceAll("(?is)<!--.*?-->"," ")
                .replaceAll("(?i)<br\\s*/?>","\n")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6]|section|article|pre)\\s*>","\n")
                .replaceAll("(?i)<(p|div|li|tr|h[1-6]|section|article|pre)\\b[^>]*>","\n");

        Spanned sp=Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY);

        String text=sp.toString().replace('\u00a0',' ');

        return text.replaceAll("[\\t\\x0B\\f\\r ]+"," ")
                .replaceAll(" *\\n *","\n");
    }

    private String lastNonEmptyLine(String text) {

        if(text==null) return "";

        String last="";

        for(String line:text.split("\\R")) {
            if(!line.trim().isEmpty()) last=line.trim();
        }

        return last;
    }

    private void speakIfChanged(String source,String line) {

        if(!running || paused || line==null || line.isEmpty()) return;

        if(line.equals(lastSpoken.get(source))) return;

        if(speak(line)) {
            lastSpoken.put(source,line);
        }
    }

    private boolean speak(String text) {

        if(!running || paused || text==null || text.isEmpty()) return false;

        try {
            if(edgeMode) {
                speakEdge(text);
                return true;
            }
            return speakLocal(text);
        } catch(Exception e) {
            update("朗读失败："+err(e));
            return false;
        }
    }

    private boolean speakLocal(String text) {

        if(!ready || tts==null) {
            update("本机语音引擎尚未就绪，稍后自动重试");
            return false;
        }

        try {
            Voice chosen=null;
            if(Build.VERSION.SDK_INT>=21) {
                chosen=findChineseVoice(localVoiceName,networkVoiceMode);
                if(chosen==null) {
                    chosen=networkVoiceMode?findBestNetworkChineseVoice():findBestOfflineChineseVoice();
                }
                if(chosen!=null) {
                    int voiceResult=tts.setVoice(chosen);
                    if(voiceResult==TextToSpeech.ERROR) {
                        update("系统语音引擎无法启用已确定的音色："+chosen.getName());
                        return false;
                    }
                } else if(networkVoiceMode) {
                    update("手机 TTS 没有可用的联网中文音色，请安装/下载中文语音数据");
                    return false;
                } else {
                    update("手机没有可用的离线中文音色，请在系统 TTS 设置中下载中文离线语音");
                    return false;
                }
            } else {
                int languageResult=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if(languageResult==TextToSpeech.LANG_MISSING_DATA ||
                        languageResult==TextToSpeech.LANG_NOT_SUPPORTED) {
                    update("手机系统 TTS 不支持中文语音");
                    return false;
                }
            }

            tts.setSpeechRate(speed);
            String id="local_"+System.currentTimeMillis();
            Bundle params=new Bundle();
            // 旧代码设置为 0，导致本机监控虽然提交朗读但完全静音。
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,Math.min(1.0f,gain));
            int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,params,id);
            if(result==TextToSpeech.ERROR) {
                update("本机 TTS 拒绝播放，检查系统语音引擎和语音数据");
                return false;
            }
            update(networkVoiceMode?"正在使用手机本机联网 TTS 朗读":"正在使用手机本机离线 TTS 朗读");
            return true;
        } catch(Exception e) {
            update("本机 TTS 异常："+err(e));
            return false;
        }
    }

    private void speakEdge(String text) throws Exception {

        if(serverUrl.isEmpty() || apiKey.isEmpty()) {
            throw new IOException("没有配置服务器地址或 API 密钥");
        }

        update("正在请求 Edge TTS");

        String endpoint=serverUrl.replaceAll("/+$","")+"/tts";

        HttpURLConnection conn=null;

        try {

            URL url=new URL(endpoint);

            conn=(HttpURLConnection)url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization","Bearer "+apiKey);
            conn.setRequestProperty("Accept","audio/mpeg");

            JSONObject json=new JSONObject();

            json.put("text",text);
            json.put("voice",edgeVoice);
            json.put("speed",(double)speed);

            byte[] body=json.toString().getBytes("UTF-8");

            conn.setFixedLengthStreamingMode(body.length);

            try(OutputStream out=conn.getOutputStream()) {
                out.write(body);
            }

            int code=conn.getResponseCode();

            if(code!=200) {

                String message="HTTP "+code;

                InputStream error=conn.getErrorStream();

                if(error!=null) {
                    message+=" "+readStreamText(error);
                }

                throw new IOException(message);
            }

            File mp3=new File(getCacheDir(),"edge_voice.mp3");

            try(InputStream in=conn.getInputStream();
                FileOutputStream out=new FileOutputStream(mp3)) {

                byte[] buf=new byte[8192];

                int n;

                while((n=in.read(buf))!=-1) {

                    if(!running) throw new IOException("服务已停止");

                    out.write(buf,0,n);
                }
            }

            update("在线语音已生成，正在解码");

            byte[] pcm=decodeMp3(mp3);

            playPcm(pcm);

            update("Edge TTS 朗读完成");

        } finally {
            if(conn!=null) conn.disconnect();
        }
    }

    private String readStreamText(InputStream in) throws Exception {

        ByteArrayOutputStream out=new ByteArrayOutputStream();

        byte[] buf=new byte[1024];

        int n;

        while((n=in.read(buf))!=-1) out.write(buf,0,n);

        return out.toString("UTF-8");
    }

    /*
     * 使用 Android MediaExtractor / MediaCodec 解码服务器返回的 MP3。
     * 输出统一转换成 PCM16，方便后续做软件增益。
     */
    private byte[] decodeMp3(File file) throws Exception {

        MediaExtractor extractor=new MediaExtractor();

        MediaCodec decoder=null;

        ByteArrayOutputStream pcm=new ByteArrayOutputStream();

        try {

            extractor.setDataSource(file.getAbsolutePath());

            int trackIndex=-1;

            MediaFormat inputFormat=null;

            for(int i=0;i<extractor.getTrackCount();i++) {

                MediaFormat f=extractor.getTrackFormat(i);

                String mime=f.getString(MediaFormat.KEY_MIME);

                if(mime!=null && mime.startsWith("audio/")) {
                    trackIndex=i;
                    inputFormat=f;
                    break;
                }
            }

            if(trackIndex<0) throw new IOException("MP3 中没有音频轨道");

            extractor.selectTrack(trackIndex);

            String mime=inputFormat.getString(MediaFormat.KEY_MIME);

            decoder=MediaCodec.createDecoderByType(mime);

            decoder.configure(inputFormat,null,null,0);

            decoder.start();

            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();

            boolean inputDone=false;
            boolean outputDone=false;

            while(!outputDone && running) {

                if(!inputDone) {

                    int index=decoder.dequeueInputBuffer(10000);

                    if(index>=0) {

                        ByteBuffer input=decoder.getInputBuffer(index);

                        if(input==null) throw new IOException("解码缓冲区不可用");

                        input.clear();

                        int size=extractor.readSampleData(input,0);

                        if(size<0) {

                            decoder.queueInputBuffer(
                                    index,0,0,0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );

                            inputDone=true;

                        } else {

                            long time=extractor.getSampleTime();

                            decoder.queueInputBuffer(
                                    index,0,size,time,0
                            );

                            extractor.advance();
                        }
                    }
                }

                int outputIndex=decoder.dequeueOutputBuffer(info,10000);

                if(outputIndex>=0) {

                    ByteBuffer output=decoder.getOutputBuffer(outputIndex);

                    if(output!=null && info.size>0) {

                        output.position(info.offset);
                        output.limit(info.offset+info.size);

                        byte[] data=new byte[info.size];

                        output.get(data);

                        pcm.write(data);
                    }

                    decoder.releaseOutputBuffer(outputIndex,false);

                    if((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {
                        outputDone=true;
                    }

                } else if(outputIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    MediaFormat format=decoder.getOutputFormat();

                    int channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ?format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;

                    if(channels<1 || channels>2) {
                        throw new IOException("暂不支持该音频声道数："+channels);
                    }
                }
            }

            if(!running) throw new IOException("服务已停止");

            byte[] result=pcm.toByteArray();

            if(result.length==0) throw new IOException("MP3 解码结果为空");

            return result;

        } finally {

            try {
                if(decoder!=null) {
                    decoder.stop();
                    decoder.release();
                }
            } catch(Exception ignored) {}

            extractor.release();
        }
    }

    /*
     * Edge TTS MP3 默认解码为 PCM16。
     * 服务器当前输出为 24kHz 单声道。
     */
    private void playPcm(byte[] input) throws Exception {

        final int sampleRate=24000;
        final int channels=AudioFormat.CHANNEL_OUT_MONO;
        final int encoding=AudioFormat.ENCODING_PCM_16BIT;

        byte[] pcm=input.clone();

        ByteBuffer b=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);

        while(b.remaining()>=2) {

            short value=b.getShort();

            double x=value/32768.0;

            double y=Math.tanh(x*gain);

            int out=(int)Math.round(y*32767.0);

            if(out>32767) out=32767;
            if(out<-32768) out=-32768;

            b.position(b.position()-2);
            b.putShort((short)out);
        }

        int min=AudioTrack.getMinBufferSize(
                sampleRate,channels,encoding
        );

        int bufferSize=Math.max(min,8192);

        AudioTrack track=new AudioTrack.Builder()
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                )
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setChannelMask(channels)
                                .setEncoding(encoding)
                                .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        synchronized(audioLock) {
            activeTrack=track;
        }

        try {

            track.play();

            int offset=0;

            while(offset<pcm.length && running) {

                while(paused && running) Thread.sleep(100);

                if(!running) break;

                int count=Math.min(8192,pcm.length-offset);

                int written=track.write(pcm,offset,count);

                if(written<0) throw new IOException("AudioTrack 写入失败："+written);

                offset+=written;
            }

            long frames=pcm.length/2;

            long deadline=System.currentTimeMillis()+
                    Math.max(5000,frames*1000L/sampleRate+5000);

            while(running &&
                    track.getPlaybackHeadPosition()<frames &&
                    System.currentTimeMillis()<deadline) {

                while(paused && running) Thread.sleep(100);

                Thread.sleep(30);
            }

        } finally {

            synchronized(audioLock) {
                if(activeTrack==track) activeTrack=null;
            }

            try {
                track.stop();
            } catch(Exception ignored) {}

            track.release();
        }
    }

    @Override
    public void onInit(int result) {

        ready=(result==TextToSpeech.SUCCESS);

        if(!ready) {
            pendingTexts.clear();
            update("本机 TTS 初始化失败，请检查手机默认文字转语音引擎");
            return;
        }

        try {

            tts.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );

            tts.setLanguage(Locale.forLanguageTag("zh-CN"));

            selectedNetworkVoice=findBestNetworkChineseVoice();

            tts.setOnUtteranceProgressListener(
                    new android.speech.tts.UtteranceProgressListener() {

                        @Override
                        public void onStart(String id) {
                            update("本机语音正在朗读");
                        }

                        @Override
                        public void onDone(String id) {
                            update("本机语音朗读完成");
                        }

                        @Override
                        public void onError(String id) {
                            update("本机语音朗读失败");
                        }

                        @Override
                        public void onError(String id,int code) {
                            update("本机语音错误："+code);
                        }
                    }
            );

            update("后台语音已就绪");
            if(worker!=null) worker.execute(()->drainPendingTexts());

        } catch(Exception e) {
            update("TTS 配置失败："+err(e));
        }
    }

    private Voice findChineseVoice(String name,boolean requireNetwork) {
        if(Build.VERSION.SDK_INT<21 || tts==null || name==null || name.isEmpty()) return null;
        try {
            Set<Voice> all=tts.getVoices();
            if(all!=null) for(Voice v:all) {
                Locale l=v.getLocale();
                if(name.equals(v.getName()) && l!=null &&
                        "zh".equalsIgnoreCase(l.getLanguage()) &&
                        v.isNetworkConnectionRequired()==requireNetwork) return v;
            }
        } catch(Exception ignored) {}
        return null;
    }

    private Voice findBestOfflineChineseVoice() {
        if(Build.VERSION.SDK_INT<21 || tts==null) return null;
        try {
            Set<Voice> all=tts.getVoices();
            Voice best=null;
            if(all!=null) for(Voice v:all) {
                Locale l=v.getLocale();
                if(l!=null && "zh".equalsIgnoreCase(l.getLanguage()) &&
                        !v.isNetworkConnectionRequired() &&
                        (best==null || v.getQuality()>best.getQuality())) best=v;
            }
            return best;
        } catch(Exception e) { return null; }
    }

    private Voice findBestNetworkChineseVoice() {

        if(Build.VERSION.SDK_INT<21 || tts==null) return null;

        try {

            Set<Voice> all=tts.getVoices();

            Voice best=null;

            if(all!=null) {
                for(Voice v:all) {

                    Locale l=v.getLocale();

                    if(l!=null &&
                            "zh".equalsIgnoreCase(l.getLanguage()) &&
                            v.isNetworkConnectionRequired()) {

                        if(best==null || v.getQuality()>best.getQuality()) {
                            best=v;
                        }
                    }
                }
            }

            return best;

        } catch(Exception e) {
            return null;
        }
    }

    private String err(Exception e) {
        return e.getMessage()==null
                ?e.getClass().getSimpleName()
                :e.getMessage();
    }

    private void stopServiceWork() {

        running=false;
        paused=false;

        if(scheduler!=null) {
            scheduler.shutdownNow();
            scheduler=null;
        }

        if(tts!=null) tts.stop();

        synchronized(audioLock) {
            if(activeTrack!=null) {
                try {
                    activeTrack.pause();
                    activeTrack.flush();
                    activeTrack.stop();
                    activeTrack.release();
                } catch(Exception ignored) {}

                activeTrack=null;
            }
        }

        try {
            if(wakeLock!=null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch(Exception ignored) {}

        stopForeground(true);
        stopSelf();
    }

    private void createChannel() {

        if(Build.VERSION.SDK_INT>=26) {

            NotificationChannel ch=new NotificationChannel(
                    CHANNEL,
                    "文件朗读监控",
                    NotificationManager.IMPORTANCE_LOW
            );

            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private Notification notification(String msg) {

        Intent p=new Intent(this,ReaderService.class);
        p.setAction(PAUSE);

        PendingIntent pi=PendingIntent.getService(
                this,
                1,
                p,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b=Build.VERSION.SDK_INT>=26
                ?new Notification.Builder(this,CHANNEL)
                :new Notification.Builder(this);

        return b.setContentTitle("文件智能朗读器")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(
                        new Notification.Action.Builder(
                                android.R.drawable.ic_media_pause,
                                "暂停",
                                pi
                        ).build()
                )
                .setOngoing(true)
                .build();
    }

    private void update(String msg) {

        try {

            if(running) {

                NotificationManager nm=
                        (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

                if(nm!=null) {
                    nm.notify(NOTIFY,notification(msg));
                }
            }

        } catch(Exception ignored) {}
    }

    @Override
    public void onDestroy() {

        running=false;
        pendingTexts.clear();

        if(scheduler!=null) scheduler.shutdownNow();

        if(worker!=null) worker.shutdownNow();

        synchronized(audioLock) {
            if(activeTrack!=null) {
                try {
                    activeTrack.stop();
                    activeTrack.release();
                } catch(Exception ignored) {}
                activeTrack=null;
            }
        }

        try {
            if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release();
        } catch(Exception ignored) {}

        if(tts!=null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}


