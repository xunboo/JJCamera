package com.jjcamera.apps.iosched.ip;

import android.os.Handler;
import android.os.HandlerThread;

import com.jjcamera.apps.iosched.util.WiFiUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class IPFinder {

	private static Handler mHandler = null;
	private static String mPublicIP = new String();

	public static synchronized String getPublicIp(){
		if(mHandler == null){
			HandlerThread thread = new HandlerThread("com.jjcamera.apps.iosched.ip");
			thread.start();
			
			mHandler = new Handler(thread.getLooper());			
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					//
					// Update IP every 30 min
					//
					mPublicIP = updatePublicIp();

					if(mPublicIP.isEmpty())
						mPublicIP = WiFiUtils.getWifiIpAddress();				

					mHandler.postDelayed(this, 1000 * 60 * 30);
				}
			});
		}

		if(mPublicIP == null || mPublicIP.isEmpty())
			mPublicIP = "127.0.0.1";
		
		return mPublicIP;
	}	

	private static String getIp(String str) throws Exception {
		URL whatismyip = new URL(str);
//				new URL("),
//				new URL() };
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
			String ip = in.readLine();
			return ip;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static String updatePublicIp() {
		String str[] = {"http://checkip.amazonaws.com", "http://icanhazip.com/", "http://ifconfig.me/ip" };
		for (int i = 0; i < str.length; i++) {
			try {
				return getIp(str[i]);
			}
			catch(Exception e){
				//e.printStackTrace();
			}
		}

		return "";
	}

	/*private static int IsWifiorMobileNetwrok(){
		ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		//For 3G check
		boolean is3g = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
		            .isConnectedOrConnecting();
		//For WiFi Check
		boolean isWifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
		            .isConnectedOrConnecting();

		System.out.println(is3g + " net " + isWifi);

		if (!is3g && !isWifi) 
		{ 
			Toast.makeText(getApplicationContext(),"Please make sure your Network Connection is ON ",Toast.LENGTH_LONG).show();
		} 
		 else 
		{ 
		        " Your method what you want to do "
		} 
	}*/

}
