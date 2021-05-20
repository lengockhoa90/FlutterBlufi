package vn.vconnex.blufi_plugin;

import java.io.ByteArrayOutputStream;

public class BlufiNotifyData {
    private int mTypeValue;
    private int mPkgType;
    private int mSubType;

    private int mFrameCtrlValue;

    private ByteArrayOutputStream mDataOS;

    BlufiNotifyData() {
        mDataOS = new ByteArrayOutputStream();
    }

    int getType() {
        return mTypeValue;
    }

    public void setType(int typeValue) {
        mTypeValue = typeValue;
    }

    public int getPkgType() {
        return mPkgType;
    }

    public void setPkgType(int pkgType) {
        mPkgType = pkgType;
    }

    public int getSubType() {
        return mSubType;
    }

    public void setSubType(int subType) {
        mSubType = subType;
    }

    int getFrameCtrl() {
        return mFrameCtrlValue;
    }

    public void setFrameCtrl(int frameCtrl) {
        mFrameCtrlValue = frameCtrl;
    }

    public void addData(byte[] bytes, int offset) {
        mDataOS.write(bytes, offset, bytes.length - offset);
    }

    public byte[] getDataArray() {
        return mDataOS.toByteArray();
    }
}
