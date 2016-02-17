/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.jjcamera.apps.iosched.streaming.audio;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import com.jjcamera.apps.iosched.streaming.MediaStream;
import com.jjcamera.apps.iosched.streaming.SessionBuilder;
import com.jjcamera.apps.iosched.streaming.exceptions.ConfNotSupportedException;
import com.jjcamera.apps.iosched.streaming.exceptions.StorageUnavailableException;
import com.jjcamera.apps.iosched.streaming.mp4.MP4Muxer;
import com.jjcamera.apps.iosched.util.SDCardUtils;
import com.jjcamera.apps.iosched.util.UIUtils;

import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/** 
 * Don't use this class directly.
 */
public abstract class AudioStream  extends MediaStream {

	protected int mAudioSource;
	protected int mOutputFormat;
	protected int mAudioEncoder;
	protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
	protected AudioQuality mQuality = mRequestedQuality.clone();
	
	public AudioStream() {
		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
	}
	
	public void setAudioSource(int audioSource) {
		mAudioSource = audioSource;
	}

	public void setAudioQuality(AudioQuality quality) {
		mRequestedQuality = quality;
	}
	
	/** 
	 * Returns the quality of the stream.  
	 */
	public AudioQuality getAudioQuality() {
		return mQuality;
	}	
	
	protected void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}
	
	protected void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}
	
	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		
		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		Log.v(TAG,"Requested audio with "+mQuality.bitRate/1000+"kbps"+" at "+mQuality.samplingRate/1000+"kHz");

		try {
			mMediaRecorder = new MediaRecorder();
			mMediaRecorder.setAudioSource(mAudioSource);
			mMediaRecorder.setOutputFormat(mOutputFormat);
			mMediaRecorder.setAudioEncoder(mAudioEncoder);
			mMediaRecorder.setAudioChannels(1);
			mMediaRecorder.setAudioSamplingRate(mQuality.samplingRate);
			mMediaRecorder.setAudioEncodingBitRate(mQuality.bitRate);
			
			// We write the output of the camera in a local socket instead of a file !			
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			FileDescriptor fd = null;
			if (sPipeApi == PIPE_API_PFD) {
				fd = mParcelWrite.getFileDescriptor();
			} else  {
				fd = mSender.getFileDescriptor();
			}
			mMediaRecorder.setOutputFile(fd);
			mMediaRecorder.setOutputFile(fd);

			mMediaRecorder.prepare();
			mMediaRecorder.start();
		}catch (Exception e) {
			mMediaRecorder = null;

			throw new ConfNotSupportedException(e.getMessage());
		}

		InputStream is = null;
		
		if (sPipeApi == PIPE_API_PFD) {
			is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
		} else  {
			try {
				// mReceiver.getInputStream contains the data from the camera
				is = mReceiver.getInputStream();
			} catch (IOException e) {
				stop();
				throw new IOException("Something happened with the local sockets :/ Start failed !");
			}
		}

		FileOutputStream fop = createTempRecorder();	

		// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setOutputStream(fop);
		mPacketizer.setInputStream(is);
		mPacketizer.start();
		mStreaming = true;
		
	}

	static public FileOutputStream createTempRecorder(){
		final String AACFILE = SDCardUtils.getExternalSdCardPathForVideo()+"/recorder" + (new Date()).getTime() + ".aac";

		Log.i(TAG, "Saving temp AACFILE file at: " + AACFILE);

		FileOutputStream fop = null;
		try {
			File file = new File(AACFILE);
			file.createNewFile();
			fop = new FileOutputStream(file);

			MP4Muxer.getInstance().setAudioSource(AACFILE);
		} catch (IOException e) {
			//throw new StorageUnavailableException(e.getMessage());
			UIUtils.DisplayToast(SessionBuilder.getInstance().getContext(), e.getMessage());
		}
		return fop;
	}
	
}
