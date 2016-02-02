package com.jjcamera.apps.iosched.streaming.mp4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;


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

	private Handler mHandler;
	private String mVideoFile;
	private String mAudioFile;
	private long mVideoTime = 0;
	private long mAudioTime = 0;

	private class MP4Info{
		public String mVideoName;
		public String mAudioName;
		public String mMp4Name;
	}

	final BlockingQueue<MP4Info> linkedBlockingQueue = new LinkedBlockingQueue<MP4Info>();
	private MP4Info mMP4Info;

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
	    HandlerThread thread = new HandlerThread("com.jjcamera.apps.iosched.streaming.mp4.mp4muxer");
		thread.start();

		mHandler = new Handler(thread.getLooper());

	}	

	/** set the orginal video/audio file source. **/
	public synchronized void setVideoSource(final String path){
		mVideoFile = path;
	}	
	
	public synchronized void setAudioSource(final String path){
		mAudioFile = path;
	}

	public synchronized void setVideoStartTime(final long time){
		mVideoTime = time;
	}	
	
	public synchronized void setAudioStartTime(final long time){
		mAudioTime = time;
	}

	public synchronized long getVideoStartTime(){
		return mVideoTime;
	}	
	
	public synchronized long getAudioStartTime(){
		return mAudioTime;
	}

	public synchronized void setVideoReady(){
		mMP4Info = new MP4Info();
		mMP4Info.mVideoName = mVideoFile;
	}

	public synchronized boolean getVideoReady(){
		return mMP4Info != null;
	}

	public synchronized void setAudioReady(){
		mMP4Info.mAudioName = mAudioFile;
	}
	
	/** the orginal files is collecting. **/
	public synchronized void collect(){		
		try {
			String timeStamp = new SimpleDateFormat("/yyyy_MM_dd_HH_mm_ss").format(new Date());
			mMP4Info.mMp4Name = timeStamp + ".mp4";		
			linkedBlockingQueue.put(mMP4Info);

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					try {
						MP4Info mi = linkedBlockingQueue.take();
						muxerFile(mi.mVideoName, mi.mAudioName, mi.mMp4Name);
					}catch (InterruptedException e) {
					}
				}				
			});
		}catch (InterruptedException e) {
		}finally{
			mMP4Info = null;
		}
	}	
	
	public synchronized void stop(){		
		try {
			String timeStamp = new SimpleDateFormat("/yyyy_MM_dd_HH_mm_ss").format(new Date());
			mMP4Info = new MP4Info();
			mMP4Info.mVideoName = mVideoFile;
			mMP4Info.mAudioName = mAudioFile;
			mMP4Info.mMp4Name = timeStamp + ".mp4";		
			linkedBlockingQueue.put(mMP4Info);

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					try {
						MP4Info mi = linkedBlockingQueue.take();
						muxerFile(mi.mVideoName, mi.mAudioName, mi.mMp4Name);
					}catch (InterruptedException e) {
					}
				}				
			});
		}catch (InterruptedException e) {
		}finally{
			mMP4Info = null;
		}
	}	
	
	public static void muxerFileDebug(){
	}

	private static void muxerFile(String videoFile, String audioFile, String outputFile){		
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
				Log.i(TAG, "video getDuration " + h264Track.getDuration() );
				Log.i(TAG, "audio getDuration " + aacTrack.getDuration() );
				Log.i(TAG, "video length (ms) " + h264Track.getSamples().size() * 1000 / UriParser.videoQuality.framerate);
				Log.i(TAG, "audio length (ms) " + aacTrack.getSamples().size() * 128);
				//int offset = 10;// 1300/ (1000 * 1024 / 8000);
				//CroppedTrack aacTrackShort = new CroppedTrack(aacTrack, offset, aacTrack.getSamples().size());
				//movie.addTrack(aacTrackShort);
				movie.addTrack(aacTrack);
			}

			Container mp4file = new DefaultMp4Builder().build(movie);

			FileChannel fc = new FileOutputStream(new File(SDCardUtils.getExternalSdCardPath() + outputFile)).getChannel();
			mp4file.writeContainer(fc);
			fc.close();

			Log.i(TAG, "finish a MP4 file...");

			new File(videoFile).delete();
        	new File(audioFile).delete();
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
}
