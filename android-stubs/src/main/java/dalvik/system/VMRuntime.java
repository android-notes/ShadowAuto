package dalvik.system;

public class VMRuntime {
    public static VMRuntime getRuntime() {
        throw new RuntimeException();
    }

    public native void clearGrowthLimit();
}
