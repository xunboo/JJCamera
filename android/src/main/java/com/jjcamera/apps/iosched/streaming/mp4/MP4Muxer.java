package com.jjcamera.apps.iosched.streaming.mp4;
/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
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


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.util.Log;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.authoring.tracks.h264.H264TrackImpl;
import com.jjcamera.apps.iosched.streaming.rtsp.UriParser;
import com.jjcamera.apps.iosched.util.SDCardUtils;


/**
 * Parse an mp4 file.
 * An mp4 file contains a tree where each node has a name and a size.
 * This class is used by H264Stream.java to determine the SPS and PPS parameters of a short video recorded by the phone.
 */
public class MP4Muxer {

	private static final String TAG = "MP4Muxer";

	private long mStart;
	private long mReady;
	private Handler mHandler;

	private String mVideoFile;
	private String mAudioFile;

	// The MP4Muxer implements the singleton pattern
	private static volatile MP4Muxer sMuxerInst = null;

	/**
	 * Returns a reference to the {@link MP4Muxer}.
	 * @return The reference to the {@link MP4Muxer}
	 */
	public final static MP4Muxer getInstance() {
		if (sMuxerInst == null) {
			synchronized (MP4Muxer.class) {
				if (sMuxerInst == null) {
					MP4Muxer.sMuxerInst = new MP4Muxer();
				}
			}
		}
		return sMuxerInst;
	}	

	private MP4Muxer(){
		clear();

		HandlerThread thread = new HandlerThread("com.jjcamera.apps.iosched.streaming.mp4.mp4muxer");
		thread.start();

		mHandler = new Handler(thread.getLooper());

	}	

	/** set the orginal video/audio file source. **/
	public void setVideoSource(final String path) throws IOException {
		mVideoFile = path;
	}	
	
	public void setAudioSource(final String path) throws IOException {
		mAudioFile = path;
	}
	
	/** the orginal files is collecting. **/
	public synchronized void start() throws IOException {
		if(mStart > 0) throw new IOException("The MP4Muxer had been started");
	
		mStart = 1;
	}	
	
	/** the orginal files are finished to collect. **/
	public synchronized void stop(){
		if(mStart == 0) return;
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				muxerFile(mVideoFile, mAudioFile);
				clear();
			}				
		});
	}	
	
	private synchronized void clear() {
		if(mVideoFile!=null) {
			//new File(mVideoFile).delete();
			mVideoFile = null;
		}

		if(mAudioFile!=null) {
			//new File(mAudioFile).delete();
			mAudioFile = null;
		}

		mStart = 0;
		mReady = 0;
	}

	public static void muxerFileDebug(){
		muxerFile(SDCardUtils.getExternalSdCardPath()+"/recorder.h264", SDCardUtils.getExternalSdCardPath()+"/recorder.aac" );
	}

	private static void muxerFile(String videoFile, String audioFile){		
		try {
			Log.i(TAG,  "generate a MP4 file...");
			
			// build a MP4 file
			H264TrackImpl h264Track = null;
			AACTrackImpl aacTrack = null;

			h264Track = new H264TrackImpl(new FileDataSourceImpl(videoFile), "eng", UriParser.videoQuality.framerate, 1);
			if(audioFile != null)
		 		aacTrack = new AACTrackImpl(new FileDataSourceImpl(audioFile));

			Movie movie = new Movie();
			movie.addTrack(h264Track);
			if(aacTrack != null){
				/*
					In AAC there are always samplerate/1024 sample/s so each sample's duration is 1000 * 1024 / samplerate milliseconds.

					48KHz => ~21.3ms
					44.1KHz => ~23.2ms
					By omitting samples from the start you can easily shorten the audio track. Remove as many as you need. You will not be able to match audio and video exactly with that but the human perception is more sensible to early audio than to late audio.
				*/
				Log.i(TAG,  "video getDuration " + h264Track.getDuration() );
				Log.i(TAG,  "audio getDuration " + aacTrack.getDuration() );
				Log.i(TAG, "audio length (ms) " + aacTrack.getSamples().size() * 128);
				//int offset = 10;// 1300/ (1000 * 1024 / 8000);
				//CroppedTrack aacTrackShort = new CroppedTrack(aacTrack, offset, aacTrack.getSamples().size());
				movie.addTrack(aacTrack);
			}

			Container mp4file = new DefaultMp4Builder().build(movie);

			FileChannel fc = new FileOutputStream(new File(SDCardUtils.getExternalSdCardPath() + "/output.mp4")).getChannel();
			mp4file.writeContainer(fc);
			fc.close();

			Log.i(TAG,  "finish a MP4 file...");
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
}
