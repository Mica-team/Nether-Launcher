package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;

public class CacheLimitPreference extends CustomSeekBarPreference {

    public CacheLimitPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CacheLimitPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getValueText(int value) {
        if (value >= 1024) {
            return "1 GB";
        }

        return value + " MB";
    }
}
