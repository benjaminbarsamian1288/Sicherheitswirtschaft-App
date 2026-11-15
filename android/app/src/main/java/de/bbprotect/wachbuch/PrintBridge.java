package de.bbprotect.wachbuch;

import android.app.Activity;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Ersetzt window.print() der Web-App: In einer WebView löst window.print()
 * nichts aus. Gedruckt wird der aktuelle Seiteninhalt (CSS @media print);
 * der Android-Druckdialog bietet auch "Als PDF speichern".
 */
public class PrintBridge {

    private final Activity activity;
    private final WebView web;

    PrintBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @JavascriptInterface
    public void print(final String title) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                String name = (title == null || title.trim().isEmpty()) ? "Dokument" : title.trim();
                PrintManager pm = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
                if (pm == null) return;
                PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(name);
                PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build();
                pm.print(name, adapter, attrs);
            }
        });
    }
}
