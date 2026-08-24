package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;

import git.artdeell.mojo.R;

public class ModIconCacheLimitPreference extends CustomSeekBarPreference {
    public static final int MIN_MB = 10;
    public static final int MAX_MB = 1024;

    private TextView mValueView;

    public ModIconCacheLimitPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ModIconCacheLimitPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mValueView = (TextView) holder.findViewById(R.id.seekbar_value);
        if (mValueView != null) {
            mValueView.setOnClickListener(v -> showManualValueDialog());
        }
        updateValueText();
    }

    @Override
    public void setValue(int value) {
        value = Math.max(MIN_MB, Math.min(MAX_MB, value));
        super.setValue(value);
        updateValueText();
    }

    private void updateValueText() {
        if (mValueView == null) return;
        mValueView.setText(formatValue(getValue()));
    }

    private String formatValue(int value) {
        if (value == MAX_MB) return "1 GB";
        return value + " MB";
    }

    private void showManualValueDialog() {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(getValue()));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(getContext())
                .setTitle("Cache limit")
                .setMessage("Enter a value from 10 MB to 1024 MB.")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int value = Integer.parseInt(input.getText().toString().trim());
                        if (value < MIN_MB || value > MAX_MB) {
                            input.setError("Enter a value from 10 to 1024 MB");
                            return;
                        }
                        setValue(value);
                        persistInt(value);
                        IconCacheJanitor.runJanitorNow();
                    } catch (NumberFormatException e) {
                        input.setError("Enter a value from 10 to 1024 MB");
                    }
                })
                .show();
    }
}
