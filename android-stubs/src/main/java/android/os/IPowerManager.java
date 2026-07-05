package android.os;

public interface IPowerManager {
    boolean isInteractive() throws RemoteException;

    public static abstract class Stub {
        public static IPowerManager asInterface(Object service) {
            throw new RuntimeException();
        }
    }
}
