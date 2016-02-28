package com.jjcamera.apps.iosched.ip;


import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Argument;
import org.cybergarage.upnp.ArgumentList;
import org.cybergarage.upnp.Device;

import android.util.Log;

import com.jjcamera.apps.iosched.ip.UpnpConstant;
import com.jjcamera.apps.iosched.ip.MappingEntity;


public class UpnpCommand
{
    private static final String tag = UpnpCommand.class.getSimpleName();

    public static String GetExternalIPAddress(Device device)
    {
        String externalIp = "";

        org.cybergarage.upnp.Service wanIPConnectionSer = device
                .getService(UpnpConstant.SERVICE_TYPE.WANIPConnection);
        if (wanIPConnectionSer != null)
        {
            Action action = wanIPConnectionSer
                    .getAction(UpnpConstant.ACTION.GetExternalIPAddress);
            if (action != null)
            {
                if (action.postControlAction())
                {
                    ArgumentList out = action.getOutputArgumentList();
                    if (out != null)
                    {
                        Argument argument = out
                                .getArgument(UpnpConstant.NewExternalIPAddress);
                        if (argument != null)
                        {
                            return argument.getValue();
                        }
                    }
                }
            }
            else
            {
                Log.d(tag, "GetExternalIPAddress no IP find");
            }
        }
        return externalIp;
    }

    public static MappingEntity GetGenericPortMappingEntry(Device device,
            int NewPortMappingIndex)
    {
        MappingEntity entity = null;

        org.cybergarage.upnp.Service wanIPConnectionSer = device
                .getService(UpnpConstant.SERVICE_TYPE.WANIPConnection);
        if (wanIPConnectionSer != null)
        {
            Action portMappingAction = wanIPConnectionSer
                    .getAction(UpnpConstant.ACTION.GetGenericPortMappingEntry);
            if (portMappingAction != null)
            {
                ArgumentList argumentList = portMappingAction.getArgumentList();
                argumentList.getArgument("NewPortMappingIndex").setValue(
                    Integer.toString(NewPortMappingIndex));
                if (portMappingAction.postControlAction())
                {
                    ArgumentList out = portMappingAction.getOutputArgumentList();
                    if (out != null)
                    {
                        entity = new MappingEntity();
                        for (int i = 0; i < out.size(); i++)
                        {
                            String key = ((Argument) out.get(i)).getName();
                            String value = ((Argument) out.get(i)).getValue();
                            if (key.equals("NewRemoteHost"))
                                entity.NewRemoteHost = value;
                            if (key.equals("NewExternalPort"))
                                entity.NewExternalPort = value;
                            if (key.equals("NewProtocol"))
                                entity.NewProtocol = value;
                            if (key.equals("NewInternalPort"))
                                entity.NewInternalPort = value;
                            if (key.equals("NewInternalClient"))
                                entity.NewInternalClient = value;
                            if (key.equals("NewEnabled"))
                                entity.NewEnabled = value;
                            if (key.equals("NewPortMappingDescription"))
                                entity.NewPortMappingDescription = value;
                            if (key.equals("NewLeaseDuration"))
                                entity.NewLeaseDuration = value;							
                        }
						Log.d(tag, entity.toString());
                    }
                }
            }
        }

        return entity;
    }

    public static boolean addPortMapping(Device dev, MappingEntity entity)
    {
        boolean success = false;
        if (dev != null && entity != null)
        {
            org.cybergarage.upnp.Service wanIPConnectionSer = dev
                    .getService(UpnpConstant.SERVICE_TYPE.WANIPConnection);
            if (wanIPConnectionSer != null)
            {
                Action addPortMappingAction = wanIPConnectionSer
                        .getAction(UpnpConstant.ACTION.AddPortMapping);
                if (addPortMappingAction != null)
                {
                    ArgumentList argumentList = addPortMappingAction.getArgumentList();
                    argumentList.getArgument("NewRemoteHost").setValue("");
                    argumentList.getArgument("NewExternalPort").setValue(
                        entity.NewExternalPort);
                    argumentList.getArgument("NewProtocol").setValue(entity.NewProtocol);
                    argumentList.getArgument("NewInternalPort").setValue(
                        entity.NewInternalPort);
                    argumentList.getArgument("NewInternalClient").setValue(
                        entity.NewInternalClient);
                    argumentList.getArgument("NewEnabled").setValue("1");
                    argumentList.getArgument("NewPortMappingDescription").setValue(
                        entity.NewPortMappingDescription);
                    argumentList.getArgument("NewLeaseDuration").setValue("0");
                    if (addPortMappingAction.postControlAction())
                    {
                        success = true;
                    }
                }
            }
        }
        return success;
    }

    public static boolean DeletePortMapping(Device dev, String external_port,
            String remote_host, String protocol)
    {
        boolean success = false;

        if (dev != null)
        {
            org.cybergarage.upnp.Service wanIPConnectionSer = dev
                    .getService(UpnpConstant.SERVICE_TYPE.WANIPConnection);
            if (wanIPConnectionSer != null)
            {
                Action delPortMappingAction = wanIPConnectionSer
                        .getAction(UpnpConstant.ACTION.DeletePortMapping);
                if (delPortMappingAction != null)
                {
                    ArgumentList argumentList = delPortMappingAction.getArgumentList();
                    argumentList.getArgument("NewExternalPort").setValue(external_port);
                    argumentList.getArgument("NewProtocol").setValue(protocol);
                    argumentList.getArgument("NewRemoteHost").setValue(remote_host);
                    if (delPortMappingAction.postControlAction())
                    {
                        success = true;
                    }
                }
            }
        }

        return success;
    }
}
