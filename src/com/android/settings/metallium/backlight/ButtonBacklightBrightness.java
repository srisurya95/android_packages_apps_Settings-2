/*
 * Copyright (C) 2015 ParanoidAndroid Legacy Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.metallium.backlight;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.ButtonSettings;

public class ButtonBacklightBrightness extends DialogPreference implements
        SeekBar.OnSeekBarChangeListener {
    private static final int DEFAULT_BUTTON_TIMEOUT = 5;

    private BrightnessControl mButtonBrightness;

    private ViewGroup mTimeoutContainer;
    private SeekBar mTimeoutBar;
    private TextView mTimeoutValue;

    private ContentResolver mResolver;

    public ButtonBacklightBrightness(Context context, AttributeSet attrs) {
        super(context, attrs);

        mResolver = context.getContentResolver();

        setDialogLayoutResource(R.layout.button_backlight);

        if (isButtonSupported()) {
            int defaultBrightness = context.getResources().getInteger(
                    com.android.internal.R.integer.config_buttonBrightnessSettingDefault);

            mButtonBrightness = new BrightnessControl(
                    Settings.System.BUTTON_BRIGHTNESS, defaultBrightness);
        }

        updateSummary();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setNeutralButton(R.string.settings_reset_button,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mTimeoutContainer = (ViewGroup) view.findViewById(R.id.timeout_container);
        mTimeoutBar = (SeekBar) view.findViewById(R.id.timeout_seekbar);
        mTimeoutValue = (TextView) view.findViewById(R.id.timeout_value);
        mTimeoutBar.setMax(30);
        mTimeoutBar.setOnSeekBarChangeListener(this);
        mTimeoutBar.setProgress(getTimeout());
        handleTimeoutUpdate(mTimeoutBar.getProgress());

        ViewGroup buttonContainer = (ViewGroup) view.findViewById(R.id.button_container);
        if (mButtonBrightness != null) {
            mButtonBrightness.init(buttonContainer);
        } else {
            buttonContainer.setVisibility(View.GONE);
            mTimeoutContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // Can't use onPrepareDialogBuilder for this as we want the dialog
        // to be kept open on click
        AlertDialog d = (AlertDialog) getDialog();
        Button defaultsButton = d.getButton(DialogInterface.BUTTON_NEUTRAL);
        defaultsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimeoutBar.setProgress(DEFAULT_BUTTON_TIMEOUT);
                if (mButtonBrightness != null) {
                    mButtonBrightness.reset();
                }
            }
        });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            return;
        }

        applyTimeout(mTimeoutBar.getProgress());
        if (mButtonBrightness != null) {
            mButtonBrightness.applyBrightness();
        }

        updateSummary();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.timeout = mTimeoutBar.getProgress();
        if (mButtonBrightness != null) {
            myState.button = mButtonBrightness.getBrightness(false);
        }

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        mTimeoutBar.setProgress(myState.timeout);
        if (mButtonBrightness != null) {
            mButtonBrightness.setBrightness(myState.button);
        }
    }

    public boolean isButtonSupported() {
        final Resources res = getContext().getResources();
        final int deviceKeys = res.getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        // All hardware keys besides volume and camera can possibly have a backlight
        boolean hasBacklightKey = (deviceKeys & ButtonSettings.KEY_MASK_HOME) != 0
                || (deviceKeys & ButtonSettings.KEY_MASK_BACK) != 0
                || (deviceKeys & ButtonSettings.KEY_MASK_MENU) != 0
                || (deviceKeys & ButtonSettings.KEY_MASK_ASSIST) != 0
                || (deviceKeys & ButtonSettings.KEY_MASK_APP_SWITCH) != 0;
        boolean hasBacklight = res.getInteger(
                com.android.internal.R.integer.config_buttonBrightnessSettingDefault) > 0;

        return hasBacklightKey && hasBacklight;
    }

    public void updateSummary() {
        if (mButtonBrightness != null) {
            int buttonBrightness = mButtonBrightness.getBrightness(true);
            int timeout = getTimeout();

            if (buttonBrightness == 0) {
                setSummary(R.string.backlight_summary_disabled);
            } else if (timeout == 0) {
                setSummary(R.string.backlight_summary_enabled);
            } else {
                setSummary(getContext().getString(R.string.backlight_summary_enabled_with_timeout,
                        getTimeoutString(timeout)));
            }
        }
    }

    private String getTimeoutString(int timeout) {
        return getContext().getResources().getQuantityString(
                R.plurals.backlight_timeout_time, timeout, timeout);
    }

    private int getTimeout() {
        return Settings.System.getInt(mResolver,
                Settings.System.BUTTON_BACKLIGHT_TIMEOUT, DEFAULT_BUTTON_TIMEOUT * 1000) / 1000;
    }

    private void applyTimeout(int timeout) {
        Settings.System.putInt(mResolver,
                Settings.System.BUTTON_BACKLIGHT_TIMEOUT, timeout * 1000);
    }

    private void updateTimeoutEnabledState() {
        int buttonBrightness = mButtonBrightness != null
                ? mButtonBrightness.getBrightness(false) : 0;
        int count = mTimeoutContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            mTimeoutContainer.getChildAt(i).setEnabled(buttonBrightness != 0);
        }
    }

    private void handleTimeoutUpdate(int timeout) {
        if (timeout == 0) {
            mTimeoutValue.setText(R.string.backlight_timeout_unlimited);
        } else {
            mTimeoutValue.setText(getTimeoutString(timeout));
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        handleTimeoutUpdate(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing here
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Do nothing here
    }

    private static class SavedState extends BaseSavedState {
        int timeout;
        int button;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            timeout = source.readInt();
            button = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(timeout);
            dest.writeInt(button);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private class BrightnessControl implements Switch.OnCheckedChangeListener {
        private String mSetting;
        private int mDefaultBrightness;
        private Switch mSwitch;
        private TextView mValue;

        public BrightnessControl(String setting, int defaultBrightness) {
            mSetting = setting;
            mDefaultBrightness = defaultBrightness;
        }

        public void init(ViewGroup container) {
            int brightness = getBrightness(true);

            mSwitch = (Switch) container.findViewById(R.id.backlight_switch);
            mSwitch.setChecked(brightness != 0);
            mSwitch.setOnCheckedChangeListener(this);

            handleBrightnessUpdate(brightness);
        }

        public int getBrightness(boolean persisted) {
            if (mSwitch != null && !persisted) {
                return mSwitch.isChecked() ? mDefaultBrightness : 0;
            }
            return Settings.System.getInt(mResolver, mSetting, mDefaultBrightness);
        }

        public void applyBrightness() {
            Settings.System.putInt(mResolver, mSetting, getBrightness(false));
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            updateTimeoutEnabledState();
        }

        public void setBrightness(int value) {
            if (mSwitch != null) {
                mSwitch.setChecked(value != 0);
            }
        }

        public void reset() {
            setBrightness(mDefaultBrightness);
        }

        private void handleBrightnessUpdate(int brightness) {
            if (mValue != null) {
                mValue.setText(String.format("%d%%", (int)((brightness * 100) / 255)));
            }
            updateTimeoutEnabledState();
        }
    }
}
