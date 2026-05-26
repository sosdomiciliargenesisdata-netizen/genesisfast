package com.sosdomiciliar.recibos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Aplicativo "SOS Recibos".
 * Abre a página index.html (que fica embutida dentro do APK, na pasta assets)
 * dentro de um WebView. Funciona 100% offline; tudo é salvo no próprio aparelho.
 */
public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: guarda os recibos e parceiros
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        try {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);  // libera a busca de CNPJ quando há internet
        } catch (Exception ignored) {}
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Um WebChromeClient é necessário para os avisos alert()/confirm() do app funcionarem.
        web.setWebChromeClient(new WebChromeClient());

        // Links externos (WhatsApp, telefone, e-mail) são abertos no app correto do sistema.
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return abrirLinkExterno(request.getUrl().toString());
            }
        });

        // Ponte que permite ao app enviar o PDF do recibo pelo WhatsApp.
        web.addJavascriptInterface(new Ponte(), "AndroidBridge");

        if (savedInstanceState == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    /** Abre http/https/whatsapp/tel/mailto no aplicativo apropriado do celular. */
    private boolean abrirLinkExterno(String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("whatsapp:") || url.startsWith("tel:")
                || url.startsWith("mailto:") || url.startsWith("sms:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "Nenhum aplicativo encontrado para abrir o link.",
                        Toast.LENGTH_SHORT).show();
            }
            return true;   // não carrega o link dentro do WebView
        }
        return false;      // a própria página (file://) carrega normalmente
    }

    // ===================== Ponte JavaScript <-> Android =====================
    private class Ponte {

        /** Recebe o PDF do recibo (em base64) e abre a tela de compartilhamento. */
        @JavascriptInterface
        public void shareReceipt(final String base64Pdf, final String fileName, final String message) {
            runOnUiThread(new Runnable() {
                public void run() { compartilharPdf(base64Pdf, fileName, message); }
            });
        }

        /** Compartilha apenas a mensagem de texto (usado se o PDF não puder ser gerado). */
        @JavascriptInterface
        public void shareText(final String message) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, message);
                    abrirChooser(i);
                }
            });
        }
    }

    private void compartilharPdf(String base64Pdf, String fileName, String message) {
        try {
            byte[] bytes = Base64.decode(base64Pdf, Base64.DEFAULT);
            File dir = new File(getCacheDir(), "recibos");
            if (!dir.exists()) dir.mkdirs();
            File pdf = new File(dir, (fileName != null && !fileName.isEmpty())
                    ? fileName : "Recibo.pdf");

            FileOutputStream fos = new FileOutputStream(pdf);
            fos.write(bytes);
            fos.close();

            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", pdf);

            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (message != null) i.putExtra(Intent.EXTRA_TEXT, message);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            abrirChooser(i);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível preparar o PDF do recibo.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void abrirChooser(Intent enviar) {
        Intent chooser = Intent.createChooser(enviar, "Enviar recibo");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum aplicativo disponível para compartilhar.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // O botão "voltar" navega no WebView (se possível) antes de fechar o app.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }
}
