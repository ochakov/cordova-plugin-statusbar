/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.cordova.statusbar;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

public class StatusBar extends CordovaPlugin {
    private static final String TAG = "StatusBar";

    private static final String ACTION_HIDE = "hide";
    private static final String ACTION_SHOW = "show";
    private static final String ACTION_READY = "_ready";
    private static final String ACTION_BACKGROUND_COLOR_BY_HEX_STRING = "backgroundColorByHexString";
    private static final String ACTION_OVERLAYS_WEB_VIEW = "overlaysWebView";
    private static final String ACTION_STYLE_DEFAULT = "styleDefault";
    private static final String ACTION_STYLE_LIGHT_CONTENT = "styleLightContent";

    private static final String STYLE_DEFAULT = "default";
    private static final String STYLE_LIGHT_CONTENT = "lightcontent";

    /**
     * cordova-android 15+ handles the status bar itself: CordovaActivity lays a "statusBarView" over the
     * top window inset and its built-in SystemBarPlugin colours that view. On Android 15+ (target SDK 35+)
     * the platform disables Window.setStatusBarColor(), so that view is the only thing visible behind the
     * status bar. Colour and overlay changes are therefore mirrored to SystemBarPlugin whenever it exists.
     */
    private static final String SYSTEM_BAR_PLUGIN = "SystemBarPlugin";

    private AppCompatActivity activity;
    private Window window;
    private String currentStyle = STYLE_LIGHT_CONTENT;

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        LOG.v(TAG, "StatusBar: initialization");
        super.initialize(cordova, webView);

        activity = this.cordova.getActivity();
        window = activity.getWindow();

        activity.runOnUiThread(() -> {
            // Clear flag FLAG_FORCE_NOT_FULLSCREEN which is set initially
            // by the Cordova.
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            // Read 'StatusBarOverlaysWebView' from config.xml, default is true.
            setStatusBarTransparent(preferences.getBoolean("StatusBarOverlaysWebView", true));

            // Read 'StatusBarBackgroundColor' from config.xml, default is #000000.
            setStatusBarBackgroundColor(preferences.getString("StatusBarBackgroundColor", "#000000"));

            // Read 'StatusBarStyle' from config.xml, default is 'lightcontent'.
            setStatusBarStyle(
                preferences.getString("StatusBarStyle", STYLE_LIGHT_CONTENT).toLowerCase()
            );
        });
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) {
        LOG.v(TAG, "Executing action: " + action);

        switch (action) {
            case ACTION_READY:
                boolean statusBarVisible = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarVisible));
                return true;

            case ACTION_SHOW:
                activity.runOnUiThread(() -> {
                    int uiOptions = window.getDecorView().getSystemUiVisibility();
                    uiOptions &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;

                    window.getDecorView().setSystemUiVisibility(uiOptions);

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                });
                return true;

            case ACTION_HIDE:
                activity.runOnUiThread(() -> {
                    int uiOptions = window.getDecorView().getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;

                    window.getDecorView().setSystemUiVisibility(uiOptions);

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                });
                return true;

            case ACTION_BACKGROUND_COLOR_BY_HEX_STRING:
                activity.runOnUiThread(() -> {
                    try {
                        setStatusBarBackgroundColor(args.getString(0));
                    } catch (JSONException ignore) {
                        LOG.e(TAG, "Invalid hexString argument, use f.i. '#777777'");
                    }
                });
                return true;

            case ACTION_OVERLAYS_WEB_VIEW:
                activity.runOnUiThread(() -> {
                    try {
                        setStatusBarTransparent(args.getBoolean(0));
                    } catch (JSONException ignore) {
                        LOG.e(TAG, "Invalid boolean argument");
                    }
                });
                return true;

            case ACTION_STYLE_DEFAULT:
                activity.runOnUiThread(() -> setStatusBarStyle(STYLE_DEFAULT));
                return true;

            case ACTION_STYLE_LIGHT_CONTENT:
                activity.runOnUiThread(() -> setStatusBarStyle(STYLE_LIGHT_CONTENT));
                return true;

            default:
                return false;
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        // SystemBarPlugin re-derives the icon colour from the background on resume (its onResume runs
        // before ours, both synchronously on the UI thread); restore the explicitly requested style.
        reapplyStatusBarStyle();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        reapplyStatusBarStyle();
    }

    private void reapplyStatusBarStyle() {
        if (hasSystemBarPlugin()) {
            activity.runOnUiThread(() -> setStatusBarStyle(currentStyle));
        }
    }

    private void setStatusBarBackgroundColor(final String colorPref) {
        if (colorPref.isEmpty()) return;

        int color;
        try {
            color = Color.parseColor(colorPref);
        } catch (IllegalArgumentException ignore) {
            LOG.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
            return;
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // SDK 19-30
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); // SDK 21
        window.setStatusBarColor(color); // SDK 21-34; disabled by Android 15+ for apps targeting SDK 35+

        if (hasSystemBarPlugin()) {
            try {
                JSONArray rgba = new JSONArray();
                rgba.put(Color.red(color));
                rgba.put(Color.green(color));
                rgba.put(Color.blue(color));
                rgba.put(Color.alpha(color) / 255.0);
                // Going through exec() (instead of painting the view directly) makes SystemBarPlugin
                // remember the colour, so its onResume / onConfigurationChanged refresh keeps it
                // rather than reverting to the StatusBarBackgroundColor preference.
                execSystemBarPlugin("setStatusBarBackgroundColor", rgba.toString());
            } catch (JSONException ignore) {
                LOG.e(TAG, "Could not forward the status bar colour to " + SYSTEM_BAR_PLUGIN);
            }
            // SystemBarPlugin picks light or dark icons from the colour's luminance; the explicit style wins.
            setStatusBarStyle(currentStyle);
        }
    }

    private void setStatusBarTransparent(final boolean isTransparent) {
        final Window window = cordova.getActivity().getWindow();
        int visibility = isTransparent
            ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            : View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE;

        window.getDecorView().setSystemUiVisibility(visibility);

        if (isTransparent) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        if (hasSystemBarPlugin()) {
            // CordovaActivity keeps the WebView below its statusBarView regardless of the SYSTEM_UI flags.
            // Collapsing that view removes the inset, so the WebView extends under the transparent status bar.
            execSystemBarPlugin("setStatusBarVisible", isTransparent ? "[false]" : "[true]");
        }
    }

    private void setStatusBarStyle(final String style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !style.isEmpty()) {
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat windowInsetsControllerCompat = WindowCompat.getInsetsController(window, decorView);

            if (style.equals(STYLE_DEFAULT)) {
                currentStyle = style;
                windowInsetsControllerCompat.setAppearanceLightStatusBars(true);
            } else if (style.equals(STYLE_LIGHT_CONTENT)) {
                currentStyle = style;
                windowInsetsControllerCompat.setAppearanceLightStatusBars(false);
            } else {
                LOG.e(TAG, "Invalid style, must be either 'default' or 'lightcontent'");
            }
        }
    }

    /**
     * @return true when running on a cordova-android that ships the built-in SystemBarPlugin (15.0.0+).
     */
    private boolean hasSystemBarPlugin() {
        PluginManager pluginManager = webView != null ? webView.getPluginManager() : null;
        return pluginManager != null && pluginManager.getPlugin(SYSTEM_BAR_PLUGIN) != null;
    }

    /**
     * Dispatches an action to SystemBarPlugin exactly like a JavaScript exec() call without callbacks
     * would ("INVALID" is the callback id cordova.js uses in that case, and results for it are dropped).
     */
    private void execSystemBarPlugin(final String action, final String rawArgs) {
        webView.getPluginManager().exec(SYSTEM_BAR_PLUGIN, action, "INVALID", rawArgs);
    }
}
