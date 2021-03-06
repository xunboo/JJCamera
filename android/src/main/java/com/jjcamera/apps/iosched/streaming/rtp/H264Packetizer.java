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

package com.jjcamera.apps.iosched.streaming.rtp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import android.annotation.SuppressLint;
import android.util.Log;

import com.jjcamera.apps.iosched.streaming.mp4.MP4Muxer;
import com.jjcamera.apps.iosched.streaming.video.VideoStream;


/**
 * 
 *   RFC 3984.
 *   
 *   H.264 streaming over RTP.
 *   
 *   Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *   
 */
public class H264Packetizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "H264Packetizer";

	private Thread t = null;
	private volatile boolean mStopped = false;
	
	private int naluLength = 0;
	private long delay = 0, oldtime = 0;
	private Statistics stats = new Statistics();
	private byte[] sps = null, pps = null, stapa = null;
	byte[] header = new byte[5];	
	private int count = 0;
	private int streamType = 1;
	private final int keyFrameSync = 1;
	
	private final static int MAX_NALU_LENGTH = 10000000;	//10MB for 720p
	private final static byte START_CODE[] = new byte[] { 0, 0, 0, 1 };		// H264 AnnexB format

	public enum NalUnitType {
	    NAL_UNKNOWN (0),
	    NAL_SLICE   (1),
	    NAL_SLICE_DPA   (2),
	    NAL_SLICE_DPB   (3),
	    NAL_SLICE_DPC   (4),
	    NAL_SLICE_IDR   (5),    /* ref_idc != 0 */	/*key frame!!!!*/
	    NAL_SEI         (6),    /* ref_idc == 0 */
	    NAL_SPS         (7),
	    NAL_PPS         (8),
	    NAL_AU_DELIMITER(9);
	    /* ref_idc == 0 for 6,9,10,11,12 */

		private int type;

		private NalUnitType(int type){
			this.type = type;
		}

		public int getType(){
			return this.type;
		}
	}


	public H264Packetizer() {
		super();
		socket.setClockFrequency(90000);
		socket.setPayloadType((byte)96);
	}

	public void start() {
		if (t == null) {
			mStopped = false;
			t = new Thread(this);
			t.setPriority(Thread.MAX_PRIORITY); 
			t.start();
		}
	}

	public void stop() {
		if (t != null) {
			//t.interrupt();
			mStopped = true;
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;

			try {
				is.close();
				if(os!=null) {			
					os.flush();
					os.close();
				}
			} catch (IOException e) {}		
		}
	}

	public void setStreamParameters(byte[] pps, byte[] sps) {
		this.pps = pps;
		this.sps = sps;

		// A STAP-A NAL (NAL type 24) containing the sps and pps of the stream
		if (pps != null && sps != null) {
			// STAP-A NAL header + NALU 1 (SPS) size + NALU 2 (PPS) size + 5 bytes
			stapa = new byte[sps.length + pps.length + 5];

			// STAP-A NAL header is 24
			stapa[0] = 24;

			// Write NALU 1 size into the array (NALU 1 is the SPS).
			stapa[1] = (byte) (sps.length >> 8);
			stapa[2] = (byte) (sps.length & 0xFF);

			// Write NALU 2 size into the array (NALU 2 is the PPS).
			stapa[sps.length + 3] = (byte) (pps.length >> 8);
			stapa[sps.length + 4] = (byte) (pps.length & 0xFF);

			// Write NALU 1 into the array, then write NALU 2 into the array.
			System.arraycopy(sps, 0, stapa, 3, sps.length);
			System.arraycopy(pps, 0, stapa, 5 + sps.length, pps.length);
		}
	}	

	public void run() {
		int run = 0;
		long duration = 0;
		Log.d(TAG,"H264 packetizer started !");
		stats.reset();
		count = 0;

		if (is instanceof MediaCodecInputStream) {
			streamType = 1;
			socket.setCacheSize(0);
		} else {
			streamType = 0;	
			socket.setCacheSize(10);
		}

		try {
			//socket.createSendThread();
			
			while (!Thread.interrupted() && !mStopped) {

				oldtime = System.nanoTime();
				
				// We read a NAL units from the input stream and we send them
				send();
				if(endstream)	break;
				
				// We measure how long it took to receive NAL units from the phone
				duration = System.nanoTime() - oldtime;
				stats.push(duration);
				// Computes the average duration of a NAL unit
				delay = stats.average();
				if(run++ < 10 || delay > 500000000)
					Log.d(TAG,"duration: "+duration/1000000+" delay: "+delay/1000000 + " ts: " + ts);
			}
		} catch (IOException e) {
			Log.e(TAG,"H264 packetizer IOException ! " + e.getMessage());		
		} catch (InterruptedException e) {}

		Log.d(TAG,"H264 packetizer stopped !");

	}

	/**
	 * Reads a NAL unit in the FIFO and sends it.
	 * If it is too big, we split it in FU-A units (RFC 3984).
	 */
	@SuppressLint("NewApi")
	private void send() throws IOException, InterruptedException {
		int sum = 1, len = 0, type;

		if (streamType == 0) {
			// NAL units are preceeded by their length, we parse the length
			fill(header,0,5);
			ts += delay;			
			naluLength = header[3]&0xFF | (header[2]&0xFF)<<8 | (header[1]&0xFF)<<16 | (header[0]&0xFF)<<24;
			if (naluLength>MAX_NALU_LENGTH || naluLength<0) resync();
		} else if (streamType == 1) {
			// NAL units are preceeded with 0x00000001
			fill(header,0,5);
			ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
			//ts += delay;
			naluLength = is.available()+1;
			if (!(header[0]==0 && header[1]==0 && header[2]==0)) {
				// Turns out, the NAL units are not preceeded with 0x00000001
				Log.e(TAG, "NAL units are not preceeded by 0x00000001");
				streamType = 2; 
				return;
			}
		} else {
			// Nothing preceededs the NAL units
			fill(header,0,1);
			header[4] = header[0];
			ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
			//ts += delay;
			naluLength = is.available()+1;
		}

		// Parses the NAL unit type
		type = header[4]&0x1F;

		if(streamType == 0 && type == 0){	//unknown, will end of stream?
			Log.d(TAG,"get 0 NAL type in the stream! Length = " +  naluLength);

			int nBufLen = Math.min(8, naluLength);
			byte pBuffer[] = new byte[nBufLen];

			if(naluLength >= 8){
				fill(pBuffer, 0, nBufLen);

				if(pBuffer[3] == 'm' && pBuffer[4] == 'o' && pBuffer[5] == 'o' && pBuffer[6] == 'v'){
					Log.d(TAG, "This is moov which is for MP4 info, ignore it and end stream");
					endstream = true;
					return;
				}
				else{
					resync();
					type = header[4]&0x1F;
				}
			}
			else{
				fill(pBuffer, 0, naluLength - 1);
				return;
			}
		}
	
		// The stream already contains NAL unit type 7 or 8, we don't need 
		// to add them to the stream ourselves
		if (type == 7 || type == 8) {
			Log.v(TAG,"SPS or PPS present in the stream.");
			count++;
			if (count>4) {
				sps = null;
				pps = null;
			}
		}

		if(type == 5 && os!=null){
			long time = MP4Muxer.getInstance().getVideoStartTime();
			long now = System.nanoTime();

			if(time == 0){
				MP4Muxer.getInstance().setVideoStartTime(now);
			}
			else if((now - time) > 60000000000L){		// 1 min
				os.flush();
				os.close();
				
				MP4Muxer.getInstance().setVideoReady();
				FileOutputStream fop = VideoStream.createTempRecorder();
				setOutputStream(fop);
				
				MP4Muxer.getInstance().setVideoStartTime(now);
			}
		}

		// We send two packets containing NALU type 7 (SPS) and 8 (PPS)
		// Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.
		if (type == 5 && sps != null && pps != null) {	
			buffer = socket.requestBuffer();
			socket.markNextPacket(buffer.mBuffers);
			socket.updateTimestamp(buffer, ts);
			System.arraycopy(stapa, 0, buffer.mBuffers, rtphl, stapa.length);
			streamWrite(START_CODE, 0, 4);
			streamWrite(stapa, 3, sps.length);
			streamWrite(START_CODE, 0, 4);
			streamWrite(stapa, sps.length + 5, pps.length);
			super.send(buffer, rtphl+stapa.length, keyFrameSync);
		}

		streamWrite(START_CODE, 0, 4);

		//Log.d(TAG,"- Nal unit length: " + naluLength + " delay: "+delay/1000000+" type: "+type);

		// Small NAL unit => Single NAL unit 
		if (naluLength<=MAXPACKETSIZE-rtphl-2) {
			buffer = socket.requestBuffer();
			buffer.mBuffers[rtphl] = header[4];
			len = fill(buffer.mBuffers, rtphl+1,  naluLength-1);
			socket.updateTimestamp(buffer, ts);
			socket.markNextPacket(buffer.mBuffers);
			streamWrite(buffer.mBuffers, rtphl, naluLength);
			super.send(buffer, naluLength+rtphl, 0);		
			//Log.d(TAG,"----- Single NAL unit - len:"+len+" delay: "+delay);
		}
		// Large NAL unit => Split nal unit 
		else {
			streamWrite(header, 4, 1);
			
			// Set FU-A header
			header[1] = (byte) (header[4] & 0x1F);  // FU header type
			header[1] += 0x80; // Start bit
			// Set FU-A indicator
			header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
			header[0] += 28;
			
			while (sum < naluLength) {
				buffer = socket.requestBuffer();
				buffer.mBuffers[rtphl] = header[0];
				buffer.mBuffers[rtphl+1] = header[1];
				socket.updateTimestamp(buffer, ts);
				if ((len = fill(buffer.mBuffers, rtphl+2,  naluLength-sum > MAXPACKETSIZE-rtphl-2 ? MAXPACKETSIZE-rtphl-2 : naluLength-sum  ))<0) return; 
				sum += len;
				// Last packet before next NAL
				if (sum >= naluLength) {
					// End bit on
					buffer.mBuffers[rtphl+1] += 0x40;
					socket.markNextPacket(buffer.mBuffers);
				}
				streamWrite(buffer.mBuffers, rtphl + 2, len);
				super.send(buffer, len+rtphl+2, 0);			
				// Switch start bit
				header[1] = (byte) (header[1] & 0x7F); 
				//Log.d(TAG,"----- FU-A unit, sum:"+sum);
			}
		}
	}

	private int fill(byte[] buffer, int offset,int length) throws IOException {
		int sum = 0, len;
		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream for fill");
			}
			else sum+=len;
		}
		return sum;
	}

	private void resync() throws IOException {
		int type;
		int len;

		Log.e(TAG,"Packetizer out of sync ! Let's try to fix that...(NAL length: "+naluLength+")");

		while (true) {

			header[0] = header[1];
			header[1] = header[2];
			header[2] = header[3];
			header[3] = header[4];
			header[4] = (byte) is.read();
			if (header[4]<0) {
				throw new IOException("End of stream for resync");
			}
			
			type = header[4]&0x1F;

			if (type == 5 || type == 1) {
				naluLength = header[3]&0xFF | (header[2]&0xFF)<<8 | (header[1]&0xFF)<<16 | (header[0]&0xFF)<<24;
				if (naluLength>0 && naluLength<=MAX_NALU_LENGTH) {
					oldtime = System.nanoTime();
					Log.e(TAG,"A NAL unit may have been found in the bit stream !");
					break;
				}
				if (naluLength==0) {
					Log.e(TAG,"NAL unit with NULL size found...");
				} else if (header[3]==0xFF && header[2]==0xFF && header[1]==0xFF && header[0]==0xFF) {
					Log.e(TAG,"NAL unit with 0xFFFFFFFF size found...");
				}
			}

		}

	}

}
