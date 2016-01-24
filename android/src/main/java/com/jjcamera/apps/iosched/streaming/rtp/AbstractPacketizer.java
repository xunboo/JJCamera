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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Random;

import com.jjcamera.apps.iosched.streaming.rtcp.SenderReport;

/**
 * 
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
abstract public class AbstractPacketizer {

	protected static final int rtphl = RtpSocket.RTP_HEADER_LENGTH;
	
	// Maximum size of RTP packets
	protected final static int MAXPACKETSIZE = RtpSocket.MTU-28;

	protected RtpSocket socket = null;
	protected InputStream is = null;
	protected OutputStream os = null;	
	protected RtpSocket.PacketBufferClass buffer;
	
	protected long ts = 0;
	protected volatile boolean startsend = false;
	protected volatile boolean endstream = false;

	public AbstractPacketizer() {
		int ssrc = new Random().nextInt();
		ts = new Random().nextInt(Integer.MAX_VALUE);
		socket = new RtpSocket();
		socket.setSSRC(ssrc);
	}

	public RtpSocket getRtpSocket() {
		return socket;
	}

	public void setSSRC(int ssrc) {
		socket.setSSRC(ssrc);
	}

	public int getSSRC() {
		return socket.getSSRC();
	}

	public void setInputStream(InputStream is) {
		this.is = is;
	}

	public void setOutputStream(OutputStream os) {
		this.os = os;
	}
	
	public void setTimeToLive(int ttl) throws IOException {
		socket.setTimeToLive(ttl);
	}

	/**
	 * Sets the destination of the stream.
	 * @param dest The destination address of the stream
	 * @param rtpPort Destination port that will be used for RTP
	 * @param rtcpPort Destination port that will be used for RTCP
	 */
	public void setDestination(InetAddress dest, int rtpPort, int rtcpPort) {
		socket.setDestination(dest, rtpPort, rtcpPort);		
	}

	/** Starts the packetizer. */
	public abstract void start();

	/** Stops the packetizer. */
	public abstract void stop();

	/** Resets the socket of packetizer. */
	public void reset(){
		socket.reset();
		startsend = false;
		endstream = false;
	}

	/** Updates data for RTCP SR and sends the packet. */
	protected void send(RtpSocket.PacketBufferClass ppb, int length) throws IOException {
		InetAddress dest = socket.getDestination();
		
		if(dest != null){
			socket.commitBuffer(ppb, dest, length);
		}
	}
	
	protected void send(RtpSocket.PacketBufferClass ppb, int length, int key) throws IOException {
		InetAddress dest = socket.getDestination();
		
		if(dest != null){		// key is for h264 
			if(key == 1 && !startsend)	{				
				startsend = true;
				//Log.v(TAG, "get first key frame type in the stream.")
			}
			if(startsend)
				socket.commitBuffer(ppb, dest, length);
		}
	}

	/** For debugging purposes. */
	protected static String printBuffer(byte[] buffer, int start,int end) {
		String str = "";
		for (int i=start;i<end;i++) str+=","+Integer.toHexString(buffer[i]&0xFF);
		return str;
	}

	protected void streamWrite(byte[] b, int off, int len) throws IOException {
		if (this.os != null)
			os.write(b, off, len);
	}

	/** Used in packetizers to estimate timestamps in RTP packets. */
	protected static class Statistics {

		public final static String TAG = "Statistics";
		
		private int count=700, c = 0;
		private float m = 0, q = 0;
		private long elapsed = 0;
		private long start = 0;
		private long duration = 0;
		private long period = 10000000000L;
		private boolean initoffset = false;
		
		public Statistics() {}
		
		public Statistics(int count, int period) {
			this.count = count;
			this.period = period;
		}
		
		public void reset() {
			initoffset = false;
			q = 0; m = 0; c = 0;
			elapsed = 0;
			start = 0;
			duration = 0;
		}
		
		public void push(long value) {
			elapsed += value;
			if (elapsed>period) {
				elapsed = 0;
				long now = System.nanoTime();
				if (!initoffset || (now - start < 0)) {
					start = now;
					duration = 0;
					initoffset = true;
				}
				// Prevents drifting issues by comparing the real duration of the 
				// stream with the sum of all temporal lengths of RTP packets. 
				value += (now - start) - duration;
				//Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
			}
			if (c<5) {
				// We ignore the first 5 measured values because they may not be accurate
				c++;
				m = value;
			} else {
				m = (m*q+value)/(q+1);
				if (q<count) q++;
			}
		}
		
		public long average() {
			long l = (long)m;
			duration += l;
			return l;
		}

	}
	
}
