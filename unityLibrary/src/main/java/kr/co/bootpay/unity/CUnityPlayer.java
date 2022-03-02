package kr.co.bootpay.unity;

import android.content.ContextWrapper;
import android.view.SurfaceView;
import android.view.View;
import com.unity3d.player.UnityPlayer;

public class CUnityPlayer extends UnityPlayer
{
    public CUnityPlayer(ContextWrapper contextwrapper) {
        super(contextwrapper);
    }

    public void addView(View child) {
        if (child instanceof SurfaceView) {
            ((SurfaceView)child).setZOrderOnTop(false);
        }
        super.addView(child);
    }
}

