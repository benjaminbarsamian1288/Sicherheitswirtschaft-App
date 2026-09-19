package de.bbprotect.wachbuch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.util.ArrayList;

/**
 * Stellt der Web-App eine Spracherkennung zur Verfügung.
 *
 * Eine WebView kennt die Web Speech API nicht. Diese Brücke bedient stattdessen
 * den SpeechRecognizer von Android; android-bridge.js bildet daraus wieder die
 * gewohnte webkitSpeechRecognition-Schnittstelle nach.
 */
public class SpeechBridge {

    private static final int RC_MIC = 2001;
    /** Bricht die Dauerschleife ab, wenn die Erkennung nie zustande kommt. */
    private static final int MAX_EMPTY_ERRORS = 5;

    private final Activity activity;
    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean gotResult;
    private int emptyErrors;
    private String pendingLang;

    SpeechBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    /* ── Aufrufe aus JavaScript ───────────────────────────────────── */

    @JavascriptInterface
    public void start(final String lang) {
        main.post(new Runnable() {
            @Override public void run() { startOnMain(lang); }
        });
    }

    @JavascriptInterface
    public void stop() {
        main.post(new Runnable() {
            @Override public void run() { stopOnMain(); }
        });
    }

    /* ── Ablauf ───────────────────────────────────────────────────── */

    private void startOnMain(String lang) {
        if (listening) return;

        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingLang = lang;
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RC_MIC);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            emit("error", "not-allowed");
            return;
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new Listener());
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null ? "de-DE" : lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        }

        gotResult = false;
        listening = true;
        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            listening = false;
            emit("error", "audio-capture");
        }
    }

    private void stopOnMain() {
        if (!listening || recognizer == null) return;
        listening = false;
        try {
            recognizer.stopListening();
            recognizer.cancel();
        } catch (Exception ignored) {
        }
        emit("end", null);
    }

    void release() {
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
    }

    /** true, wenn das Ergebnis zu dieser Brücke gehört. */
    boolean onPermissionsResult(int requestCode, int[] results) {
        if (requestCode != RC_MIC) return false;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startOnMain(pendingLang);
        } else {
            emit("error", "not-allowed");
        }
        pendingLang = null;
        return true;
    }

    /* ── Erkennungs-Callbacks ─────────────────────────────────────── */

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle partial) {
            String text = first(partial);
            if (text != null && !text.isEmpty()) {
                gotResult = true;
                emit("result", text, false);
            }
        }

        @Override
        public void onResults(Bundle results) {
            String text = first(results);
            listening = false;
            if (text != null && !text.isEmpty()) {
                gotResult = true;
                emptyErrors = 0;
                emit("result", text, true);
            }
            emit("end", null);
        }

        @Override
        public void onError(int error) {
            listening = false;
            if (gotResult) emptyErrors = 0; else emptyErrors++;

            // Wiederholt gar kein Ton oder Treffer: Schleife beenden statt endlos neu starten
            if (emptyErrors >= MAX_EMPTY_ERRORS) {
                emptyErrors = 0;
                emit("error", "not-allowed");
                return;
            }
            emit("error", webError(error));
        }

        private String first(Bundle b) {
            if (b == null) return null;
            ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return (list == null || list.isEmpty()) ? null : list.get(0);
        }
    }

    private static String webError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "not-allowed";
            case SpeechRecognizer.ERROR_AUDIO:                     return "audio-capture";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:           return "network";
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:            return "no-speech";
            default:                                               return "aborted";
        }
    }

    /* ── Rückmeldung an JavaScript ────────────────────────────────── */

    private void emit(String kind, String arg) {
        emit(kind, arg, false);
    }

    private void emit(final String kind, final String arg, final boolean isFinal) {
        final String js;
        if ("result".equals(kind)) {
            js = "window.__androidSpeech&&window.__androidSpeech.result(" + quote(arg) + "," + isFinal + ")";
        } else if ("error".equals(kind)) {
            js = "window.__androidSpeech&&window.__androidSpeech.error(" + quote(arg) + ")";
        } else {
            js = "window.__androidSpeech&&window.__androidSpeech.end()";
        }
        main.post(new Runnable() {
            @Override public void run() {
                try {
                    web.evaluateJavascript(js, null);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static String quote(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\'': sb.append("\\'"); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case ' ': sb.append("\\u2028"); break;
                case ' ': sb.append("\\u2029"); break;
                default: sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }
}
