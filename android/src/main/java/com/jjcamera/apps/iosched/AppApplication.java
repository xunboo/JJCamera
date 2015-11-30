/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.jjcamera.apps.iosched;

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.security.ProviderInstaller;
import com.jjcamera.apps.iosched.settings.SettingsUtils;
import com.jjcamera.apps.iosched.util.AnalyticsHelper;

import android.app.Application;
import android.content.Intent;
import android.util.DisplayMetrics;


import static com.jjcamera.apps.iosched.util.LogUtils.LOGE;
import static com.jjcamera.apps.iosched.util.LogUtils.LOGW;
import static com.jjcamera.apps.iosched.util.LogUtils.makeLogTag;

/**
 * {@link android.app.Application} used to initialize Analytics. Code initialized in
 * Application classes is rare since this code will be run any time a ContentProvider, Activity,
 * or Service is used by the user or system. Analytics, dependency injection, and multi-dex
 * frameworks are in this very small set of use cases.
 */
public class AppApplication extends Application {

    private static final String TAG = makeLogTag(AppApplication.class);

	private static DisplayMetrics     displayMetrics = null;

	protected static AppApplication       mInstance;

	public AppApplication(){
        mInstance = this;
    }

    public static AppApplication getApp() {
        if (mInstance != null && mInstance instanceof AppApplication) {
            return (AppApplication) mInstance;
        } else {
            mInstance = new AppApplication();
            mInstance.onCreate();
            return (AppApplication) mInstance;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AnalyticsHelper.prepareAnalytics(getApplicationContext());
        SettingsUtils.markDeclinedWifiSetup(getApplicationContext(), false);

		mInstance = this;

        // Ensure an updated security provider is installed into the system when a new one is
        // available via Google Play services.
        try {
            ProviderInstaller.installIfNeededAsync(getApplicationContext(),
                    new ProviderInstaller.ProviderInstallListener() {
                        @Override
                        public void onProviderInstalled() {
                            LOGW(TAG, "New security provider installed.");
                        }

                        @Override
                        public void onProviderInstallFailed(int errorCode, Intent intent) {
                            LOGE(TAG, "New security provider install failed.");
                            // No notification shown there is no user intervention needed.
                        }
                    });
        } catch (Exception ignorable) {
            LOGE(TAG, "Unknown issue trying to install a new security provider.", ignorable);
        }
    }


	public float getScreenDensity() {
		if (this.displayMetrics == null) {
			setDisplayMetrics(getResources().getDisplayMetrics());
		}
		return this.displayMetrics.density;
	}

	public int getScreenHeight() {
		if (this.displayMetrics == null) {
			setDisplayMetrics(getResources().getDisplayMetrics());
		}
		return this.displayMetrics.heightPixels;
	}

	public int getScreenWidth() {
		if (this.displayMetrics == null) {
			setDisplayMetrics(getResources().getDisplayMetrics());
		}
		return this.displayMetrics.widthPixels;
	}

	public void setDisplayMetrics(DisplayMetrics DisplayMetrics) {
		this.displayMetrics = DisplayMetrics;
	}

	public int dp2px(float f)
	{
		return (int)(0.5F + f * getScreenDensity());
	}

	public int px2dp(float pxValue) {
		return (int) (pxValue / getScreenDensity() + 0.5f);
	}

	public String getFilesDirPath() {
		return getFilesDir().getAbsolutePath();
	}

	public String getCacheDirPath() {
		return getCacheDir().getAbsolutePath();
	}
}
