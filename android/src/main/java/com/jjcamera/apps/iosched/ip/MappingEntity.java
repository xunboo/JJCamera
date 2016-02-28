package com.jjcamera.apps.iosched.ip;


import android.os.Parcel;
import android.os.Parcelable;


public class MappingEntity implements Parcelable
{
    public MappingEntity()
    {
    }

    public String NewRemoteHost = "";
    public String NewExternalPort = "";
    public String NewProtocol = "";
    public String NewInternalPort = "";
    public String NewInternalClient = "";
    public String NewEnabled = "";
    public String NewPortMappingDescription = "";
    public String NewLeaseDuration = "";

    @Override
    public String toString()
    {
        return "NewRemoteHost=" + this.NewRemoteHost + "\n" + "NewExternalPort="
            + this.NewExternalPort + "\n" + "NewProtocol=" + this.NewProtocol + "\n"
            + "NewInternalPort=" + this.NewInternalPort + "\n" + "NewInternalClient="
            + this.NewInternalClient + "\n" + "NewEnabled=" + this.NewEnabled + "\n"
            + "NewPortMappingDescription=" + this.NewPortMappingDescription + "\n"
            + "NewLeaseDuration=" + this.NewLeaseDuration + "\n";
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(this.NewRemoteHost);
        dest.writeString(this.NewExternalPort);
        dest.writeString(this.NewProtocol);
        dest.writeString(this.NewInternalPort);
        dest.writeString(this.NewInternalClient);
        dest.writeString(this.NewEnabled);
        dest.writeString(this.NewPortMappingDescription);
        dest.writeString(this.NewLeaseDuration);
    }

    protected MappingEntity(Parcel in)
    {
        this.NewRemoteHost = in.readString();
        this.NewExternalPort = in.readString();
        this.NewProtocol = in.readString();
        this.NewInternalPort = in.readString();
        this.NewInternalClient = in.readString();
        this.NewEnabled = in.readString();
        this.NewPortMappingDescription = in.readString();
        this.NewLeaseDuration = in.readString();
    }

    public static final Creator<MappingEntity> CREATOR = new Creator<MappingEntity>()
    {
        public MappingEntity createFromParcel(Parcel source)
        {
            return new MappingEntity(source);
        }

        public MappingEntity[] newArray(int size)
        {
            return new MappingEntity[size];
        }
    };
}
