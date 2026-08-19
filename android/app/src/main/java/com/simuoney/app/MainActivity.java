package com.simuoney.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // localStorage : memorise les saisies et le theme

        // Tout ce qui n'est pas l'application elle-meme est confie au systeme :
        // le mail de resiliation s'ouvre dans la messagerie, les numeros dans le
        // telephone, les sites dans le navigateur. L'application reste ouverte.
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest requete) {
                Uri lien = requete.getUrl();
                if ("file".equals(lien.getScheme())) {
                    return false;
                }
                try {
                    Intent intention = new Intent(Intent.ACTION_VIEW, lien);
                    intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intention);
                } catch (ActivityNotFoundException e) {
                    // aucune application installee pour ce type de lien
                }
                return true;
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
