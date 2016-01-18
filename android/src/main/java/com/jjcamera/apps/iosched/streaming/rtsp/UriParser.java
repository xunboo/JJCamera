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

package com.jjcamera.apps.iosched.streaming.rtsp;

import static com.jjcamera.apps.iosched.streaming.SessionBuilder.AUDIO_AAC;
import static com.jjcamera.apps.iosched.streaming.SessionBuilder.AUDIO_AMRNB;
import static com.jjcamera.apps.iosched.streaming.SessionBuilder.AUDIO_NONE;
import static com.jjcamera.apps.iosched.streaming.SessionBuilder.VIDEO_H263;
import static com.jjcamera.apps.iosched.streaming.SessionBuilder.VIDEO_H264;
import static com.jjcamera.apps.iosched.streaming.SessionBuilder.VIDEO_NONE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Set;

import com.jjcamera.apps.iosched.streaming.MediaStream;
import com.jjcamera.apps.iosched.streaming.Session;
import com.jjcamera.apps.iosched.streaming.SessionBuilder;
import com.jjcamera.apps.iosched.streaming.audio.AudioQuality;
import com.jjcamera.apps.iosched.streaming.video.VideoQuality;

import android.content.ContentValues;
import android.hardware.Camera.CameraInfo;


/**
 * This class parses URIs received by the RTSP server and configures a Session accordingly.
 */
public class UriParser {

	public final static String TAG = "UriParser";

	
	/** Default quality of audio streams. */
	public static AudioQuality audioQuality = new AudioQuality(8000,32000);

	/** Default quality of video streams. */
	public static VideoQuality videoQuality = new VideoQuality(1280,720,20,5000000);

	/** By default AMR is the audio encoder. */
	public static int audioEncoder = SessionBuilder.AUDIO_AAC;

	/** By default H.264 is the video encoder. */
	public static int videoEncoder = SessionBuilder.VIDEO_H264;

	public static Session mCurrentSession = null;


	public static Session easyparse() throws IllegalStateException, IOException {
		if(mCurrentSession != null)		return mCurrentSession;
		
		byte audioApi = MediaStream.MODE_MEDIARECORDER_API;
		byte videoApi = MediaStream.MODE_MEDIARECORDER_API;
		//byte videoApi = MediaStream.MODE_MEDIACODEC_API;	
		
		SessionBuilder builder = SessionBuilder.getInstance().clone();

		//if(videoApi == MediaStream.MODE_MEDIACODEC_API)		//test purpose
		//	videoQuality = new VideoQuality(640,480,20,500000);

		builder.setFlashEnabled(false);
		builder.setCamera(CameraInfo.CAMERA_FACING_BACK);

		builder.setVideoQuality(videoQuality).setVideoEncoder(videoEncoder);
		builder.setAudioQuality(audioQuality).setAudioEncoder(audioEncoder);

		Session session = builder.build();
		
		if (session.getVideoTrack() != null) {
			session.getVideoTrack().setStreamingMethod(videoApi);
		}
		
		if (session.getAudioTrack() != null) {
			session.getAudioTrack().setStreamingMethod(audioApi);
		}

		mCurrentSession = session;

		return session;
	}

	public static Session getSession() {
		return mCurrentSession;
	}	

	public static void clearSession() {
		mCurrentSession = null;
	}

	
	public static Session parse(String uri) throws IllegalStateException, IOException {
		SessionBuilder builder = SessionBuilder.getInstance().clone();
		byte audioApi = 0, videoApi = 0;

        String[] queryParams = URI.create(uri).getQuery().split("&");
        ContentValues params = new ContentValues();
        for(String param:queryParams)
        {
            String[] keyValue = param.split("=");
			String value = "";
			try {
				value = keyValue[1];
			}catch(ArrayIndexOutOfBoundsException e){}

            params.put(
                    URLEncoder.encode(keyValue[0], "UTF-8"), // Name
                    URLEncoder.encode(value, "UTF-8")  // Value
            );

        }

		if (params.size()>0) {

			builder.setAudioEncoder(AUDIO_NONE).setVideoEncoder(VIDEO_NONE);
            Set<String> paramKeys=params.keySet();
			// Those parameters must be parsed first or else they won't necessarily be taken into account
            for(String paramName: paramKeys) {
                String paramValue = params.getAsString(paramName);

				// FLASH ON/OFF
				if (paramName.equalsIgnoreCase("flash")) {
					if (paramValue.equalsIgnoreCase("on")) 
						builder.setFlashEnabled(true);
					else 
						builder.setFlashEnabled(false);
				}

				// CAMERA -> the client can choose between the front facing camera and the back facing camera
				else if (paramName.equalsIgnoreCase("camera")) {
					if (paramValue.equalsIgnoreCase("back")) 
						builder.setCamera(CameraInfo.CAMERA_FACING_BACK);
					else if (paramValue.equalsIgnoreCase("front")) 
						builder.setCamera(CameraInfo.CAMERA_FACING_FRONT);
				}

				// MULTICAST -> the stream will be sent to a multicast group
				// The default mutlicast address is 228.5.6.7, but the client can specify another
				else if (paramName.equalsIgnoreCase("multicast")) {
					if (paramValue!=null) {
						try {
							InetAddress addr = InetAddress.getByName(paramValue);
							if (!addr.isMulticastAddress()) {
								throw new IllegalStateException("Invalid multicast address !");
							}
							builder.setDestination(paramValue);
						} catch (UnknownHostException e) {
							throw new IllegalStateException("Invalid multicast address !");
						}
					}
					else {
						// Default multicast address
						builder.setDestination("228.5.6.7");
					}
				}

				// UNICAST -> the client can use this to specify where he wants the stream to be sent
				else if (paramName.equalsIgnoreCase("unicast")) {
					if (paramValue!=null) {
						builder.setDestination(paramValue);
					}					
				}
				
				// VIDEOAPI -> can be used to specify what api will be used to encode video (the MediaRecorder API or the MediaCodec API)
				else if (paramName.equalsIgnoreCase("videoapi")) {
					if (paramValue!=null) {
						if (paramValue.equalsIgnoreCase("mr")) {
							videoApi = MediaStream.MODE_MEDIARECORDER_API;
						} else if (paramValue.equalsIgnoreCase("mc")) {
							videoApi = MediaStream.MODE_MEDIACODEC_API;
						}
					}					
				}
				
				// AUDIOAPI -> can be used to specify what api will be used to encode audio (the MediaRecorder API or the MediaCodec API)
				else if (paramName.equalsIgnoreCase("audioapi")) {
					if (paramValue!=null) {
						if (paramValue.equalsIgnoreCase("mr")) {
							audioApi = MediaStream.MODE_MEDIARECORDER_API;
						} else if (paramValue.equalsIgnoreCase("mc")) {
							audioApi = MediaStream.MODE_MEDIACODEC_API;
						}
					}					
				}		

				// TTL -> the client can modify the time to live of packets
				// By default ttl=64
				else if (paramName.equalsIgnoreCase("ttl")) {
					if (paramValue!=null) {
						try {
							int ttl = Integer.parseInt(paramValue);
							if (ttl<0) throw new IllegalStateException();
							builder.setTimeToLive(ttl);
						} catch (Exception e) {
							throw new IllegalStateException("The TTL must be a positive integer !");
						}
					}
				}

				// H.264
				else if (paramName.equalsIgnoreCase("h264")) {
					VideoQuality quality = VideoQuality.parseQuality(paramValue);
					builder.setVideoQuality(quality).setVideoEncoder(VIDEO_H264);
				}

				// H.263
				else if (paramName.equalsIgnoreCase("h263")) {
					VideoQuality quality = VideoQuality.parseQuality(paramValue);
					builder.setVideoQuality(quality).setVideoEncoder(VIDEO_H263);
				}

				// AMR
				else if (paramName.equalsIgnoreCase("amrnb") || paramName.equalsIgnoreCase("amr")) {
					AudioQuality quality = AudioQuality.parseQuality(paramValue);
					builder.setAudioQuality(quality).setAudioEncoder(AUDIO_AMRNB);
				}

				// AAC
				else if (paramName.equalsIgnoreCase("aac")) {
					AudioQuality quality = AudioQuality.parseQuality(paramValue);
					builder.setAudioQuality(quality).setAudioEncoder(AUDIO_AAC);
				}

			}

		}

		if (builder.getVideoEncoder()==VIDEO_NONE && builder.getAudioEncoder()==AUDIO_NONE) {
			SessionBuilder b = SessionBuilder.getInstance();
			builder.setVideoEncoder(b.getVideoEncoder());
			builder.setAudioEncoder(b.getAudioEncoder());
		}

		Session session = builder.build();
		
		if (videoApi>0 && session.getVideoTrack() != null) {
			session.getVideoTrack().setStreamingMethod(videoApi);
		}
		
		if (audioApi>0 && session.getAudioTrack() != null) {
			session.getAudioTrack().setStreamingMethod(audioApi);
		}
		
		return session;

	}

}
