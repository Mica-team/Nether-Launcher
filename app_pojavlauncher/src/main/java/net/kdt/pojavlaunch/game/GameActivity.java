package net.kdt.pojavlaunch.game;

import static net.kdt.pojavlaunch.Tools.dialogForceClose;
import static net.kdt.pojavlaunch.game.platform.Platform.PLATFORM;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.CallbackBridge;
import net.kdt.pojavlaunch.utils.KeycodeUtils;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.customcontrols.mouse.HotbarView;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.backend.DummyBackend;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.QuickSettingSideDialog;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;
import net.kdt.pojavlaunch.utils.jre.GameRunner;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

import git.artdeell.mojo.R;

public class GameActivity extends BaseActivity
        implements ControlButtonMenuListener, EditorExitable, ServiceConnection {

    public static final String INTENT_LAUNCH_VERSION = "intent_version";
    public static final String INTENT_LAUNCH_CLASSPATH = "intent_classpath";

    public static TouchCharInput touchCharInput;
    private GameView launcherGLView;
    private static WeakReference<GameCursorView> weakCursor;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private View mLoadingScreen;

    Instance instance;
    Account account;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    public static int mForcedPanningHeight = 0;
    public static int mImeHeight = 0;

    /*
     * Jarvis easter egg.
     *
     * Spaces are removed and comparison is case-insensitive.
     */
    private boolean isJarvisAccount() {
        if (account == null || account.username == null) {
            return false;
        }

        String username = account.username
                .replace(" ", "")
                .trim();

        return username.equalsIgnoreCase("jarvis");
    }

    /*
     * Plays the Jarvis easter egg sound.
     *
     * Minecraft continues loading normally.
     */
    private void playJarvisEasterEgg() {
        if (!isJarvisAccount()) {
            return;
        }

        try {
            MediaPlayer player =
                    MediaPlayer.create(
                            this,
                            R.raw.welcome_back
                    );

            if (player == null) {
                Log.w(
                        "JarvisEasterEgg",
                        "Could not create MediaPlayer"
                );
                return;
            }

            player.setOnCompletionListener(mp -> {
                mp.release();
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(
                        "JarvisEasterEgg",
                        "MediaPlayer error: " +
                                what +
                                ", " +
                                extra
                );

                mp.release();
                return true;
            });

            player.start();

            Log.i(
                    "JarvisEasterEgg",
                    "Jarvis easter egg triggered"
            );

        } catch (Throwable e) {
            /*
             * The easter egg must NEVER prevent
             * Minecraft from launching.
             */
            Log.w(
                    "JarvisEasterEgg",
                    "Could not play Jarvis easter egg",
                    e
            );
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = Instances.loadSelectedInstance();
        account = Accounts.getCurrent();

        /*
         * Trigger the easter egg after the current
         * account has been loaded.
         *
         * This does NOT wait for the sound.
         * Minecraft continues normally.
         */
        playJarvisEasterEgg();

        if (instance == null) {
            Toast.makeText(
                    this,
                    R.string.instance_dir_missing,
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        AsyncAssetManager.extractDefaultSettings(
                this,
                instance.getGameDirectory()
        );

        MCOptionUtils.load(
                instance.getGameDirectory().getAbsolutePath()
        );

        Intent gameServiceIntent =
                new Intent(this, GameService.class);

        // Start the service a bit early
    }

    public void hideLoadingScreen() {
        if (mLoadingScreen == null) {
            return;
        }

        ((TextView) mLoadingScreen.findViewById(
                R.id.main_loading_screen_text
        )).setText(
                getString(
                        R.string.loading_screen_booted,
                        PLATFORM.backendName()
                )
        );

        mLoadingScreen.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    ((ViewGroup) mLoadingScreen.getParent())
                            .removeView(mLoadingScreen);

                    mLoadingScreen = null;
                })
                .start();
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout(
                    (CustomControls) null
            );

            mControlLayout.setModifiable(false);

            System.gc();

            mControlLayout.loadLayout(
                    instance.getLaunchControls()
            );

            mDrawerPullButton.setVisibility(
                    mControlLayout.hasMenuButton()
                            ? View.GONE
                            : View.VISIBLE
            );

        } catch (Exception e) {
            Tools.showError(this, e);
        }

        navDrawer.setAdapter(
                gameActionArrayAdapter
        );

        navDrawer.setOnItemClickListener(
                gameActionClickListener
        );

        isInEditor = false;
    }

    @Override
    public void onServiceConnected(
            ComponentName name,
            IBinder service
    ) {
        GameService.LocalBinder localBinder =
                (GameService.LocalBinder) service;

        mServiceBinder = localBinder;

        launcherGLView.start(
                localBinder.isActive
        );

        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(
            ComponentName name
    ) {
    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch
     * the captured mouse events as trackball events due to a bug.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(
            MotionEvent event
    ) {
        int eventSource = event.getSource();

        return (eventSource &
                InputDevice.SOURCE_MOUSE_RELATIVE) != 0
                ||
                (eventSource &
                        InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(
            MotionEvent ev
    ) {
        if (Tools.isAndroid8OrHigher()
                && checkCaptureDispatchConditions(ev)) {

            return launcherGLView
                    .dispatchCapturedPointerEvent(ev);

        } else {
            return super.dispatchTrackballEvent(ev);
        }
    }
}
