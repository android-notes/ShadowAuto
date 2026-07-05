package android.content.pm;

import android.os.IBinder;

public interface IPackageManager {
    public abstract class Stub {

        public static Object asInterface(IBinder b) {
            throw new RuntimeException();
        }
    }
}
