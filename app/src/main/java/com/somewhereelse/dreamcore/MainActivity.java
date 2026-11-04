package com.somewhereelse.dreamcore;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int MEDIA_PERMISSION_REQUEST = 402;
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(23,22,43));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(16,15,32));
        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(23,22,43));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.addJavascriptInterface(new AlbumBridge(), "DreamcoreAlbum");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }, MEDIA_PERMISSION_REQUEST);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            }, MEDIA_PERMISSION_REQUEST);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MEDIA_PERMISSION_REQUEST);
        }
    }

    private JSONArray queryCollection(Uri collection, String type) {
        JSONArray result = new JSONArray();
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            "bucket_id",
            "bucket_display_name"
        };
        try (Cursor cursor = getContentResolver().query(
                collection, projection, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int bucketIdCol = cursor.getColumnIndex("bucket_id");
            int bucketNameCol = cursor.getColumnIndex("bucket_display_name");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                Uri itemUri = ContentUris.withAppendedId(collection, id);
                JSONObject item = new JSONObject();
                item.put("uri", itemUri.toString());
                item.put("title", cursor.getString(nameCol) == null ? "Untitled" : cursor.getString(nameCol));
                item.put("date", cursor.getLong(dateCol));
                item.put("mime", cursor.getString(mimeCol) == null ? "" : cursor.getString(mimeCol));
                item.put("type", type);
                String bucketName = bucketNameCol >= 0 ? cursor.getString(bucketNameCol) : null;
                String bucketId = bucketIdCol >= 0 ? cursor.getString(bucketIdCol) : null;
                item.put("album", bucketName == null || bucketName.trim().isEmpty() ? "Other" : bucketName);
                item.put("albumId", bucketId == null ? "other" : bucketId);
                result.put(item);
            }
        } catch (SecurityException ignored) {
            // Android 14+ may grant access to only a selected subset or one media type.
        } catch (Exception ignored) { }
        return result;
    }

    private JSONArray getDeviceMedia() {
        JSONArray all = new JSONArray();
        if (!hasMediaPermission()) return all;
        JSONArray images = queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "photo");
        JSONArray videos = queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video");
        for (int i = 0; i < images.length(); i++) all.put(images.optJSONObject(i));
        for (int i = 0; i < videos.length(); i++) all.put(videos.optJSONObject(i));
        return all;
    }

    private class AlbumBridge {
        @JavascriptInterface public void requestMediaAccess() {
            runOnUiThread(() -> {
                if (hasMediaPermission()) notifyPage(true);
                else requestMediaPermission();
            });
        }
        @JavascriptInterface public String getDeviceMediaJson() {
            return getDeviceMedia().toString();
        }
        @JavascriptInterface public boolean hasMediaAccess() {
            return hasMediaPermission();
        }
    }

    private void notifyPage(boolean granted) {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                "window.onNativeMediaChanged && window.onNativeMediaChanged(" + (granted ? "true" : "false") + ")", null));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST) notifyPage(hasMediaPermission());
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) notifyPage(hasMediaPermission());
    }

    @Override public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("(function(){var v=document.getElementById('viewer');if(v&&v.classList.contains('open')){window.closeViewerFromNative();return 'viewer';}return 'page';})()", value -> {
                if (!"\"viewer\"".equals(value)) {
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else MainActivity.super.onBackPressed();
                }
            });
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("DreamcoreAlbum");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}