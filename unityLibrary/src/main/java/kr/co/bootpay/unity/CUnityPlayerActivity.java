package kr.co.bootpay.unity;

import android.os.Bundle;

//import com.unity3d.player.UnityPlayerActivity;


import com.unity3d.player.*;

public class CUnityPlayerActivity  extends UnityPlayerActivity
{
    @Override
    public void onCreate(Bundle bundle) {
        requestWindowFeature(1);
        super.onCreate(bundle);
        getWindow().setFormat(2);
        mUnityPlayer = new CUnityPlayer(this);
        setContentView(mUnityPlayer);
        mUnityPlayer.requestFocus();
    }
}
