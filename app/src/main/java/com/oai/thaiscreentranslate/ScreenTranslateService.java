package com.oai.thaiscreentranslate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.Gravity;
import android.view.WindowManager;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ScreenTranslateService extends Service {
    public static final String ACTION_START = "com.oai.thaiscreentranslate.START";
    public static final String ACTION_STOP = "com.oai.thaiscreentranslate.STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL = "screen_translate";
    private static final int NOTIFY = 7801;
    private static final long SCAN_INTERVAL_MS = 650L;
    private static final Pattern HAN = Pattern.compile(
            ".*[\\u3400-\\u4DBF\\u4E00-\\u9FFF].*", Pattern.DOTALL);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LruCache<String, String> translationCache = new LruCache<>(800);

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private Translator translator;
    private WindowManager wm;
    private OverlayView overlay;

    private boolean modelReady;
    private boolean modelDownloading;
    private boolean busy;
    private boolean captureWanted;
    private int width;
    private int height;
    private int density;
    private long frameNo;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!busy && projection != null) {
                captureWanted = true;
                if (overlay != null) overlay.setVisibility(android.view.View.INVISIBLE);
            }
            main.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFY, notification("กำลังเริ่ม OCR และโมเดลแปล…"));

        recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());

        TranslatorOptions opts = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.THAI)
                .build();
        translator = Translation.getClient(opts);

        prepareTranslationModel();
    }

    private void prepareTranslationModel() {
        if (modelReady || modelDownloading || translator == null) return;
        modelDownloading = true;
        updateNotification("กำลังดาวน์โหลด/ตรวจโมเดลจีน→ไทย…");

        translator.downloadModelIfNeeded()
                .addOnSuccessListener(v -> {
                    modelDownloading = false;
                    modelReady = true;
                    updateNotification("พร้อมแปลจีน→ไทยทั้งหน้าจอ");
                    setOverlayStatus("พร้อมแปล • กำลังสแกนทั้งหน้าจอ");
                })
                .addOnFailureListener(e -> {
                    modelDownloading = false;
                    modelReady = false;
                    String msg = shortError(e);
                    updateNotification("โมเดลแปลยังไม่พร้อม • จะลองใหม่");
                    setOverlayStatus("OCR ทำงาน • โมเดลแปลยังไม่พร้อม: " + msg);
                    main.postDelayed(this::prepareTranslationModel, 12000L);
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data;
            if (Build.VERSION.SDK_INT >= 33) {
                data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
            } else {
                data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            }
            if (data != null) startProjection(code, data);
        }

        return START_STICKY;
    }

    private void startProjection(int code, Intent data) {
        if (projection != null) return;

        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(code, data);
        if (projection == null) {
            updateNotification("เริ่มจับภาพหน้าจอไม่สำเร็จ");
            stopSelf();
            return;
        }

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                main.post(() -> stopSelf());
            }
        }, main);

        readScreenMetrics();

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(this::onImage, main);

        virtualDisplay = projection.createVirtualDisplay(
                "ThaiScreenTranslate",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                main);

        createOverlay();
        setOverlayStatus(modelReady
                ? "พร้อมแปล • กำลังจับภาพ"
                : "กำลังจับภาพ • รอโมเดลแปล");

        captureWanted = true;
        main.post(ticker);
    }

    private void readScreenMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager baseWm =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        if (Build.VERSION.SDK_INT >= 30) {
            Rect r = baseWm.getCurrentWindowMetrics().getBounds();
            width = r.width();
            height = r.height();
            density = getResources().getDisplayMetrics().densityDpi;
        } else {
            baseWm.getDefaultDisplay().getRealMetrics(dm);
            width = dm.widthPixels;
            height = dm.heightPixels;
            density = dm.densityDpi;
        }
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new OverlayView(this);
        overlay.setSourceSize(width, height);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);

        p.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        wm.addView(overlay, p);
        overlay.setVisibility(android.view.View.VISIBLE);
    }

    private void onImage(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || !captureWanted || busy) return;

            captureWanted = false;
            busy = true;
            frameNo++;

            Bitmap bmp = toBitmap(image);

            if (overlay != null) overlay.setVisibility(android.view.View.VISIBLE);
            worker.submit(() -> runOcr(bmp));
        } catch (Exception e) {
            busy = false;
            setOverlayStatus("จับภาพผิดพลาด: " + shortError(e));
        } finally {
            if (image != null) image.close();
        }
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();

        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        int paddedWidth = width + Math.max(0, rowPadding / Math.max(1, pixelStride));
        Bitmap padded = Bitmap.createBitmap(
                paddedWidth,
                height,
                Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);

        Bitmap crop = Bitmap.createBitmap(padded, 0, 0, width, height);
        if (padded != crop) padded.recycle();
        return crop;
    }

    private void runOcr(Bitmap bmp) {
        try {
            Text result = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(bmp, 0)));

            List<SourceLine> chinese = new ArrayList<>();

            for (Text.TextBlock block : result.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    String src = line.getText() == null ? "" : line.getText().trim();
                    Rect box = line.getBoundingBox();
                    if (box != null && !src.isEmpty() && HAN.matcher(src).matches()) {
                        chinese.add(new SourceLine(new Rect(box), src));
                    }
                }
            }

            if (chinese.isEmpty()) {
                postResult(new ArrayList<>(),
                        "OCR ทำงาน • ไม่พบข้อความจีน • #" + frameNo);
                return;
            }

            if (!modelReady) {
                prepareTranslationModel();
                postResult(new ArrayList<>(),
                        "พบจีน " + chinese.size() + " จุด • กำลังเตรียมโมเดลแปล");
                return;
            }

            List<OverlayView.Item> out = new ArrayList<>();
            int failed = 0;

            for (SourceLine s : chinese) {
                String th = translationCache.get(s.text);

                if (th == null) {
                    try {
                        th = Tasks.await(translator.translate(s.text));
                        if (th != null) {
                            th = th.trim();
                            if (!th.isEmpty()) translationCache.put(s.text, th);
                        }
                    } catch (Exception e) {
                        failed++;
                        th = null;
                    }
                }

                if (th != null && !th.isEmpty()) {
                    out.add(new OverlayView.Item(s.box, th));
                }
            }

            String state = "พบจีน " + chinese.size()
                    + " • แปล " + out.size()
                    + (failed > 0 ? " • พลาด " + failed : "");
            postResult(out, state);

        } catch (Exception e) {
            postResult(new ArrayList<>(),
                    "OCR ผิดพลาด: " + shortError(e));
        } finally {
            bmp.recycle();
        }
    }

    private void postResult(List<OverlayView.Item> out, String state) {
        main.post(() -> {
            if (overlay != null) {
                overlay.setItems(out);
                overlay.setStatus(state);
                overlay.setVisibility(android.view.View.VISIBLE);
            }
            busy = false;
        });
    }

    private void setOverlayStatus(String state) {
        main.post(() -> {
            if (overlay != null) {
                overlay.setStatus(state);
                overlay.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private static class SourceLine {
        final Rect box;
        final String text;
        SourceLine(Rect box, String text) {
            this.box = box;
            this.text = text;
        }
    }

    private String shortError(Throwable e) {
        if (e == null) return "ไม่ทราบสาเหตุ";
        String s = e.getMessage();
        if (s == null || s.trim().isEmpty()) s = e.getClass().getSimpleName();
        s = s.replace('\n', ' ').trim();
        return s.length() > 52 ? s.substring(0, 52) : s;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c =
                    new NotificationChannel(
                            CHANNEL,
                            "แปลหน้าจอ",
                            NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi =
                PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_IMMUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CHANNEL)
                        : new Notification.Builder(this);

        return b.setContentTitle("แปลหน้าจอไทย")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager)
                getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFY, notification(text));
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(ticker);

        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) { }
        }
        if (virtualDisplay != null) virtualDisplay.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (recognizer != null) recognizer.close();
        if (translator != null) translator.close();

        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
