package com.jjcamera.apps.iosched.explore.data;

/**
 * Data describing an Explore item to be displayed on the Explore screen.
 */

public class CameraData extends ItemGroup {

	private String mDeviceName;
		
    public CameraData() {
		mDeviceName = "Camera";
    }


	public String getDevice() { return mDeviceName; }
}
