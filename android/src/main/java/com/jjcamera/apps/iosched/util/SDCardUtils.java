package com.jjcamera.apps.iosched.util;

import android.os.Environment;
import android.os.storage.StorageManager;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/*-----------
some possible External Storage paths: (Sorry couldn't find original source ):

/storage/sdcard1 //!< Motorola Xoom
/storage/extsdcard //!< Samsung SGS3
/storage/sdcard0/external_sdcard // user request
/mnt/extsdcard
/mnt/sdcard/external_sd //!< Samsung galaxy family
/mnt/external_sd
/mnt/media_rw/sdcard1 //!< 4.4.2 on CyanogenMod S3
/removable/microsd //!< Asus transformer prime
/mnt/emmc
/storage/external_SD //!< LG
/storage/ext_sd //!< HTC One Max
/storage/removable/sdcard1 //!< Sony Xperia Z1
/data/sdext
/data/sdext2
/data/sdext3
/data/sdext4
------------*/

public class SDCardUtils {

	public static String getExternalSdCardPath() {
	        String path = null;
	        
	        File sdCardFile = null;
			List<String> sdCardPossibleRoot = Arrays.asList("/mnt/", "/storage/");
	        List<String> sdCardPossiblePath = Arrays.asList("external_SD", "external_sd", "ext_sd", "external", "extSdCard", "sdcard1");

			for (String sdRoot : sdCardPossibleRoot) {
		        for (String sdPath : sdCardPossiblePath) {
		                File file = new File(sdRoot, sdPath);
		                
		                if (file.isDirectory() && file.canWrite()) {
		                        path = file.getAbsolutePath() + "/JJCamera";
		                        
		                        File testWritable = new File(path);

								if(testWritable.isDirectory())
									break;
		                        
		                        if (testWritable.mkdirs()) {
									break;
		                        }
		                        else {
		                        	path = null;
		                        }
		                }
		        }
			}
	        
	        if (path != null) {
	        	sdCardFile = new File(path);
	        }
	        else {
	        	sdCardFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/JJCamera");
				if(!sdCardFile.exists())
					sdCardFile.mkdirs();
				else if(!sdCardFile.isDirectory()){
					sdCardFile.delete();
					sdCardFile.mkdirs();
				}
	        }
	        
	        return sdCardFile.getAbsolutePath();
	}

	/*public static String getExternalSdCardPath2()
	{
		File file = new File("/system/etc/vold.fstab");
		FileReader fr = null;
		BufferedReader br = null;

		try {
		    fr = new FileReader(file);
		} catch (FileNotFoundException e) {
		    e.printStackTrace();
		} 

		try {
		    if (fr != null) {
		        br = new BufferedReader(fr);
		        String s = br.readLine();
		        while (s != null) {
		            if (s.startsWith("dev_mount")) {
		                String[] tokens = s.split("\\s");
		                path = tokens[2]; //mount_point
		                if (!Environment.getExternalStorageDirectory().getAbsolutePath().equals(path)) {
		                    break;
		                }
		            }
		            s = br.readLine();
		        }
		    }            
		} catch (IOException e) {
		    e.printStackTrace();
		} finally {
		    try {
		        if (fr != null) {
		            fr.close();
		        }            
		        if (br != null) {
		            br.close();
		        }
		    } catch (IOException e) {
		        e.printStackTrace();
		    }
		}
	}*/

	public static String getExternalSdCardPathForVideo() {
		File sdCardFile = new File(getExternalSdCardPath() + "/Video");
		if(!sdCardFile.exists())
			sdCardFile.mkdirs();
		else if(!sdCardFile.isDirectory()){
			sdCardFile.delete();
			sdCardFile.mkdirs();
		}

		return sdCardFile.getAbsolutePath();
	}

}

