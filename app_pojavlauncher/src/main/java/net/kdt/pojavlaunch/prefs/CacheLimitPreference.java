package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;

import git.artdeell.mojo.R;

public class CacheLimitPreference extends CustomSeekBarPreference {
    private static final int MIN_MB = 10;
    private static final int MAX_MB = 1024;
    private static final int UNLIMITED = 0;

    public CacheLimitPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_cache_limit);
    }

    public CacheLimitPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_cache_limit);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView valueView = holder.itemView.findViewById(R.id.seekbar_value);
        SeekBar seekBar = holder.itemView.findViewById(R.id.seekbar);
        if (valueView == null || seekBar == null) return;

        valueView.setText(formatLimit(normalize(getValue())));
        valueView.setFocusable(false);
        valueView.setClickable(false);

        seekBar.setMax(MAX_MB);
        seekBar.setProgress(normalize(getValue()));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = normalize(progress);
                valueView.setText(formatLimit(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                int value = normalize(bar.getProgress());
                setValue(value);
                valueView.setText(formatLimit(value));
                IconCacheJanitor.runJanitor();
            }
        });
    }

    private int normalize(int value) {
        if (value <= 0) return UNLIMITED;
        return Math.max(MIN_MB, Math.min(MAX_MB, value));
    }

    private String formatLimit(int value) {
        if (value == UNLIMITED) return getContext().getString(R.string.cache_limit_unlimited);
        if (value == MAX_MB) return "1 GB";
        return value + " MB";
    }

    @SuppressWarnings("unused")
    private void showEditDialog(TextView valueView, SeekBar seekBar) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setHint(R.string.cache_limit_edit_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(String.valueOf(getValue()));
        input.selectAll();

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.cache_limit_edit_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer value = parseLimit(input.getText().toString());
            if (value == null) {
                Toast.makeText(getContext(), R.string.cache_limit_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            setValue(value);
            seekBar.setProgress(value);
            valueView.setText(formatLimit(value));
            IconCacheJanitor.runJanitor();
            dialog.dismiss();
        }));
        dialog.show();
        input.requestFocus();
    }

    private Integer parseLimit(String raw) {
        String value = raw.trim().toLowerCase();
        if (value.equals("unlimited") || value.equals("none")) return UNLIMITED;

        try {
            if (value.endsWith("gb")) {
                double gb = Double.parseDouble(value.substring(0, value.length() - 2).trim());
                return normalize((int) Math.round(gb * 1024));
            }
            if (value.endsWith("mb")) value = value.substring(0, value.length() - 2).trim();
            return normalize(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
