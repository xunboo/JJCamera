package com.jjcamera.apps.iosched.streaming.mp4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;


import android.util.Log;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Mp4TrackImpl;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.authoring.tracks.h264.H264TrackImpl;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;
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
		public Matrix mMatrix;
	}

	final BlockingQueue<MP4Info> linkedBlockingQueue = new LinkedBlockingQueue<MP4Info>();
	private MP4Info mMP4Info;

	private static Matrix sMatrix = Matrix.ROTATE_0;
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

	public static void setRotationMatrix(int iDegree){
	 	if(iDegree == 90)
			sMatrix = Matrix.ROTATE_90;
		else if(iDegree == 180)
			sMatrix = Matrix.ROTATE_180;
		else
			sMatrix = Matrix.ROTATE_0;
	}

	/*private Matrix getRotationMatrix(){
		int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
		Matrix m = Matrix.ROTATE_0;
		switch (rotation) {
		    case Surface.ROTATION_0:
		        m = Matrix.ROTATE_90;
		        break;
		    case Surface.ROTATION_90:
		        m = Matrix.ROTATE_0;
		        break;
		    case Surface.ROTATION_180:
		        m = Matrix.ROTATE_90;
		        break;
		    case Surface.ROTATION_270:
		        m = Matrix.ROTATE_180;
		        break;
		}	

		return m;
	}*/
		
	/** the orginal files is collecting. **/
	public synchronized void collect(){		
		try {
			String timeStamp = new SimpleDateFormat("/yyyy_MM_dd_HH_mm_ss").format(new Date());
			mMP4Info.mMp4Name = timeStamp + ".mp4";	
			mMP4Info.mMatrix = sMatrix;
			
			linkedBlockingQueue.put(mMP4Info);

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					try {
						MP4Info mi = linkedBlockingQueue.take();
						muxerFile(mi);
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
			mMP4Info.mMatrix = sMatrix;
			linkedBlockingQueue.put(mMP4Info);

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					try {
						MP4Info mi = linkedBlockingQueue.take();
						muxerFile(mi);
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
		try 
		{
			File input = new File(SDCardUtils.getExternalSdCardPath() + "/a.h264");
			File output = new File(SDCardUtils.getExternalSdCardPath() + "/b.mp4");

			H264TrackImpl h264Track = new H264TrackImpl(new FileDataSourceImpl(input), "eng", UriParser.videoQuality.framerate, 1);
			Movie m = new Movie();
			m.addTrack(h264Track);
			m.setMatrix(Matrix.ROTATE_90);
			Container out = new DefaultMp4Builder().build(m);
			MovieHeaderBox mvhd = Path.getPath(out, "moov/mvhd");
    		mvhd.setMatrix(Matrix.ROTATE_90);
			TrackBox trackBox  = Path.getPath(out, "moov/trak");
			TrackHeaderBox tkhd = trackBox.getTrackHeaderBox();
			tkhd.setMatrix(Matrix.ROTATE_90);
			FileChannel fc = new FileOutputStream(output.getAbsolutePath()).getChannel();
			out.writeContainer(fc);
			fc.close();

		} 
		catch (IOException e) {
		    Log.e("test", "some exception", e);
		}	
	}

	private static void muxerFile(MP4Info mi){		

		String videoFile = mi.mVideoName, audioFile = mi.mAudioName, outputFile = mi.mMp4Name;
		Matrix mMatrix = mi.mMatrix;

		File fVideo = new File(videoFile), fAudio = new File(audioFile);

		if(fVideo.length()==0){
			fVideo.delete();
        	fAudio.delete();

			return;
		}

		if(fAudio.length()==0){
        	fAudio.delete();
			audioFile = null;
		}	
		
		try {
			Log.i(TAG,  "generate a MP4 file...");
			
			// build a MP4 file
			H264TrackImpl h264Track = null;
			AACTrackImpl aacTrack = null;

			h264Track = new H264TrackImpl(new FileDataSourceImpl(videoFile), "eng", UriParser.videoQuality.framerate, 1);
			if(audioFile != null)
		 		aacTrack = new AACTrackImpl(new FileDataSourceImpl(audioFile));

			Movie movie = new Movie();
			movie.setMatrix(mMatrix);
			movie.addTrack(h264Track);
			h264Track.getTrackMetaData().setMatrix(mMatrix);
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

			FileChannel fc = new FileOutputStream(new File(SDCardUtils.getExternalSdCardPathForVideo() + outputFile)).getChannel();
			mp4file.writeContainer(fc);
			fc.close();

			Log.i(TAG, "finish a MP4 file...");
		}
		catch(Exception e) {
			e.printStackTrace();
		}	

		fVideo.delete();
        fAudio.delete();
	}
	
}
