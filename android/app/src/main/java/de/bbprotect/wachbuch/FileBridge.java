package de.bbprotect.wachbuch;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Legt Exporte der Web-App (CSV-Auswertung, JSON-Backup) im Ordner "Download" ab.
 * In einer WebView lösen Blob-Downloads sonst nichts aus.
 */
public class FileBridge {

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    FileBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void save(final String name, final String mime, final String base64) {
        new Thread(new Runnable() {
            @Override public void run() {
                String safe = sanitize(name);
                try {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    writeToDownloads(safe, mime, data);
                    toast(activity.getString(R.string.saved_to_downloads, safe));
                } catch (Exception e) {
                    toast(activity.getString(R.string.save_failed));
                }
            }
        }).start();
    }

    private void writeToDownloads(String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = activity.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty()
                    ? "application/octet-stream" : mime);
            cv.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri item = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (item == null) throw new IllegalStateException("insert failed");

            OutputStream out = cr.openOutputStream(item);
            if (out == null) throw new IllegalStateException("stream failed");
            try {
                out.write(data);
            } finally {
                out.close();
            }
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(item, cv, null, null);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
            FileOutputStream out = new FileOutputStream(new File(dir, name));
            try {
                out.write(data);
            } finally {
                out.close();
            }
        }
    }

    private static String sanitize(String name) {
        String n = (name == null || name.trim().isEmpty()) ? "export.txt" : name.trim();
        n = n.replaceAll("[/\\\\:*?\"<>|]", "_");
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
