package com.jjcamera.apps.iosched.ip;

import org.cybergarage.upnp.ControlPoint;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.device.DeviceChangeListener;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;


import com.jjcamera.apps.iosched.AppApplication;
import com.jjcamera.apps.iosched.ip.UpnpConstant;
import com.jjcamera.apps.iosched.ip.MappingEntity;
import com.jjcamera.apps.iosched.ip.UpnpCommand;
import com.jjcamera.apps.iosched.streaming.rtsp.RtspServer;
import com.jjcamera.apps.iosched.util.WiFiUtils;

import java.util.ArrayList;
import java.util.List;


public class UpnpService extends Service
{
    private static final String TAG = UpnpService.class.getSimpleName();

    private UpnpThread thread;

    private static Device curDevice = null;
    private static List<MappingEntity> itemList = new ArrayList<MappingEntity>();

    private final static String _start_find_igd_device = "start_find_igd_device ";		//Internet Gateway Device 
    private final static String _start_find_router = "start_find_router ";
    private final static String _find_local_ip = "find_local_ip ";
    private final static String _find_device = "find_device ";
    private final static String _external_ip = "external_ip ";
	private final static String _finish_add_device = "finish_add_device ";

	private final static String _UPNP_PROTOCOL = "TCP";
	private final static String _UPNP_DESCRIPT = "JJCamera";

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        thread.destroy();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        thread = new UpnpThread();
        thread.start();
    }

    public static Device GetCurDevice() {
        return curDevice;
    }

    private static void sendMessage(Message msg){
        Log.d(TAG, msg.what + "-" + msg.obj.toString() );
    }

    private static class UpnpThread extends Thread
    {
        private ControlPoint controlPoint;
        private boolean isDestroyed = false;

        @Override
        public void destroy()
        {
            controlPoint.stop();
            isDestroyed = true;
        }

        @Override
        public void run()
        {
            controlPoint = new ControlPoint();
            controlPoint.start();
            Message msg = Message.obtain();
            msg.what = UpnpConstant.MSG.find_start;
            msg.obj = _start_find_igd_device;
            sendMessage(msg);

            controlPoint.addDeviceChangeListener(new DeviceChangeListener()
            {
                @Override
                public void deviceAdded(Device dev)
                {
                    if (dev == null)
                        return;

                    Log.d(TAG, "UPNP Device is found = " + dev.getModelName() + ", description url = " + dev.getLocation());

                    String deviceType = dev.getDeviceType();
                    if (deviceType.equals(UpnpConstant.IGD))
                    {
                        curDevice = dev;

                        Message msg = Message.obtain();
                        msg.what = UpnpConstant.MSG.find_ok;
                        msg.obj = _find_device + dev.getModelName();
                        sendMessage(msg);

                        String externalIp = UpnpCommand.GetExternalIPAddress(dev);
                        Message ipMsg = Message.obtain();
                        ipMsg.what = UpnpConstant.MSG.find_ok;
                        ipMsg.obj = _external_ip + externalIp;
                        sendMessage(ipMsg);

                        for (int i = 0; i < 10000; i++)
                        {
                            MappingEntity entity = UpnpCommand
                                    .GetGenericPortMappingEntry(dev, i);
                            if (entity != null)
                            {
                                if (!itemList.contains(entity)){
                                    itemList.add(entity);
                                }
								
                                Message mappingMsg = Message.obtain();
                                mappingMsg.what = UpnpConstant.MSG.find_ok;
                                ipMsg.obj = "get mapping entity";
                                sendMessage(ipMsg);
                            }
                            else
                            {
                                //Log.d(TAG, "deviceAdded Unknown");
                                break;
                            }

                            if (isDestroyed)
                                break;
                        }

						boolean bFindExist = false;
						for(MappingEntity entity: itemList)	{
							if(entity.NewExternalPort.equals(Integer.toString(RtspServer.DEFAULT_RTSP_PORT))
								&& entity.NewProtocol.equals(_UPNP_PROTOCOL) ){
								if(entity.NewPortMappingDescription.equals(_UPNP_DESCRIPT)){
									bFindExist = true;
									break;
								}
								else{
									UpnpCommand.DeletePortMapping(dev, entity.NewExternalPort, entity.NewRemoteHost, entity.NewProtocol);
								}
							}
						}

						if(!bFindExist){
	                        MappingEntity entity = new MappingEntity();
	                        entity.NewPortMappingDescription = _UPNP_DESCRIPT;
	                        entity.NewInternalPort = Integer.toString(RtspServer.DEFAULT_RTSP_PORT);
	                        entity.NewExternalPort = Integer.toString(RtspServer.DEFAULT_RTSP_PORT);
	                        entity.NewProtocol = _UPNP_PROTOCOL;
	                        entity.NewInternalClient = WiFiUtils.getWifiIpAddress();

	                        if(UpnpCommand.addPortMapping(dev, entity)){
                                Message addMsg = Message.obtain();
		                        ipMsg.what = UpnpConstant.MSG.add_port_ok;
		                        ipMsg.obj = "Add port ok";
		                        sendMessage(ipMsg);							
	                        }
							else{
                                Message addMsg = Message.obtain();
		                        ipMsg.what = UpnpConstant.MSG.add_port_fail;
		                        ipMsg.obj = "Add port fail";
		                        sendMessage(ipMsg);									
							}                           
						}
                        else {
                            Message addMsg = Message.obtain();
                            ipMsg.what = UpnpConstant.MSG.add_port_fail;
                            ipMsg.obj = "Add port fail since it has existed";
                            sendMessage(ipMsg);
                        }

                        Message endMsg = Message.obtain();
                        endMsg.what = UpnpConstant.MSG.find_end;
						endMsg.obj = _finish_add_device;
                        sendMessage(endMsg);
                    }
                }

                @Override
                public void deviceRemoved(Device dev)
                {
                }
            });
        }

    }
}

