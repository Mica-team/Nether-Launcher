package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {

    private static final String BATTERY_SAVER_KEY = "batterySaver";
    private static final String BACKUP_VALID = "batterySaverBackupValid";
    private static final String BACKUP_RESOLUTION_PRESENT = "batterySaverBackupResolutionPresent";
    private static final String BACKUP_RESOLUTION_VALUE = "batterySaverBackupResolutionValue";
    private static final String BACKUP_VSYNC_PRESENT = "batterySaverBackupVsyncPresent";
    private static final String BACKUP_VSYNC_VALUE = "batterySaverBackupVsyncValue";
    private static final String BACKUP_SUSTAINED_PRESENT = "batterySaverBackupSustainedPresent";
    private static final String BACKUP_SUSTAINED_VALUE = "batterySaverBackupSustainedValue";

    private boolean mApplyingBatterySaver;
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        // Show ANGLE switch only if AnglePlugin is available
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(getContext(), LibraryPlugin.ID_ANGLE_PLUGIN);
        SwitchPreferenceCompat angleSwitch = requirePreference("use_angle", SwitchPreferenceCompat.class);
        angleSwitch.setVisible(angle != null);
        angleSwitch.setChecked(LauncherPreferences.PREF_USE_ANGLE);

        // Same but for ZINK plugin
        SwitchPreference legacyZink = requirePreference("zinkForceLegacy", SwitchPreference.class);
        legacyZink.setChecked(LauncherPreferences.PREF_ZINK_FORCE_LEGACY);
        if(!Architecture.isx86Device()) {
            LibraryPlugin zink = LibraryPlugin.discoverPlugin(getContext(), LibraryPlugin.ID_ZINK_PLUGIN);
            legacyZink.setVisible(zink != null);
        }
        else {
            legacyZink.setVisible(false);
        }

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        RendererCompatUtil.RenderersList renderersList = RendererCompatUtil.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(renderersList.rendererDisplayNames);
        rendererListPreference.setEntryValues(renderersList.rendererIds.toArray(new String[0]));

        computeVisibility();
        updateBatterySaverControls();
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if(activity != null) {
            requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(activity));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        if (BATTERY_SAVER_KEY.equals(s) && !mApplyingBatterySaver) {
            applyBatterySaver(p, p.getBoolean(BATTERY_SAVER_KEY, false));
        }
        super.onSharedPreferenceChanged(p, s);
        updateBatterySaverControls();
        computeVisibility();
    }

    private void applyBatterySaver(SharedPreferences preferences, boolean enabled) {
        mApplyingBatterySaver = true;
        try {
            SharedPreferences.Editor editor = preferences.edit();
            if (enabled) {
                // Snapshot the user's current settings once, so turning Battery Saver off restores them.
                if (!preferences.getBoolean(BACKUP_VALID, false)) {
                    editor.putBoolean(BACKUP_RESOLUTION_PRESENT, preferences.contains("resolutionRatio"));
                    editor.putInt(BACKUP_RESOLUTION_VALUE, preferences.getInt("resolutionRatio",
                            (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100)));
                    editor.putBoolean(BACKUP_VSYNC_PRESENT, preferences.contains("force_vsync"));
                    editor.putBoolean(BACKUP_VSYNC_VALUE, preferences.getBoolean("force_vsync",
                            LauncherPreferences.PREF_FORCE_VSYNC));
                    editor.putBoolean(BACKUP_SUSTAINED_PRESENT, preferences.contains("sustainedPerformance"));
                    editor.putBoolean(BACKUP_SUSTAINED_VALUE, preferences.getBoolean("sustainedPerformance",
                            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE));
                    editor.putBoolean(BACKUP_VALID, true);
                }
                editor.putInt("resolutionRatio", 50);
                editor.putBoolean("force_vsync", true);
                editor.putBoolean("sustainedPerformance", true);
            } else {
                if (preferences.getBoolean(BACKUP_VALID, false)) {
                    restoreIntPreference(editor, preferences, "resolutionRatio",
                            BACKUP_RESOLUTION_PRESENT, BACKUP_RESOLUTION_VALUE);
                    restoreBooleanPreference(editor, preferences, "force_vsync",
                            BACKUP_VSYNC_PRESENT, BACKUP_VSYNC_VALUE);
                    restoreBooleanPreference(editor, preferences, "sustainedPerformance",
                            BACKUP_SUSTAINED_PRESENT, BACKUP_SUSTAINED_VALUE);
                }
                editor.remove(BACKUP_VALID)
                        .remove(BACKUP_RESOLUTION_PRESENT).remove(BACKUP_RESOLUTION_VALUE)
                        .remove(BACKUP_VSYNC_PRESENT).remove(BACKUP_VSYNC_VALUE)
                        .remove(BACKUP_SUSTAINED_PRESENT).remove(BACKUP_SUSTAINED_VALUE);
            }
            editor.apply();
        } finally {
            mApplyingBatterySaver = false;
        }
    }

    private static void restoreIntPreference(SharedPreferences.Editor editor, SharedPreferences preferences,
                                             String key, String presentKey, String valueKey) {
        if (preferences.getBoolean(presentKey, false)) {
            editor.putInt(key, preferences.getInt(valueKey, 100));
        } else {
            editor.remove(key);
        }
    }

    private static void restoreBooleanPreference(SharedPreferences.Editor editor, SharedPreferences preferences,
                                                 String key, String presentKey, String valueKey) {
        if (preferences.getBoolean(presentKey, false)) {
            editor.putBoolean(key, preferences.getBoolean(valueKey, false));
        } else {
            editor.remove(key);
        }
    }

    private void updateBatterySaverControls() {
        boolean enabled = LauncherPreferences.DEFAULT_PREF != null
                && LauncherPreferences.DEFAULT_PREF.getBoolean(BATTERY_SAVER_KEY, false);
        requirePreference("resolutionRatio", CustomSeekBarPreference.class).setEnabled(!enabled);
        requirePreference("sustainedPerformance", SwitchPreference.class).setEnabled(!enabled);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setEnabled(!enabled);
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }
}
