package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ICancellationSignal;
import android.os.RemoteException;

public interface IContentProvider {
    Cursor query(String callingPkg, String attributionTag, Uri url,
                 String[] projection,
                 Bundle queryArgs, ICancellationSignal cancellationSignal)
            throws RemoteException;

    Cursor query(AttributionSource attributionSource, Uri url,
                 String[] projection,
                 Bundle queryArgs, ICancellationSignal cancellationSignal)
            throws RemoteException;

    Cursor query(String callingPkg, Uri url, String[] projection,
                 Bundle queryArgs, ICancellationSignal cancellationSignal)
            throws RemoteException;

    Cursor query(String callingPkg, Uri url, String[] projection, String selection,
               String[] selectionArgs, String sortOrder, ICancellationSignal cancellationSignal)
                       throws RemoteException;
}
