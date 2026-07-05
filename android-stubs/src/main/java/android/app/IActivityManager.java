package android.app;

import android.content.IContentProvider;
import android.os.IBinder;

public interface IActivityManager {
//    IActivityManager.ContentProviderHolder getContentProviderExternal(
//            String name, int userId, IBinder token);
//
//    ContentProviderHolder getContentProviderExternal(
//            String name, int userId, IBinder token);

    android.app.ContentProviderHolder getContentProviderExternal(
            String name, int userId, IBinder token, String tag);

    void removeContentProviderExternalAsUser(String name, IBinder token, int userId);

    void removeContentProviderExternal(String name, IBinder token);
    public static class ContentProviderHolder {
        public IContentProvider provider;
    }

}
