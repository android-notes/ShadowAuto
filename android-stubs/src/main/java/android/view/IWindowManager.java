package android.view;

import android.os.RemoteException;
import android.window.ScreenCapture;


public interface IWindowManager {
    void captureDisplay(int displayId, ScreenCapture.CaptureArgs captureArgs, ScreenCapture.ScreenCaptureListener listener) throws RemoteException;
}
