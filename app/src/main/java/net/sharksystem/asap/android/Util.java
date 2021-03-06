package net.sharksystem.asap.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;

import net.sharksystem.utils.Utils;

import java.io.File;

public class Util {
    public static void unregisterBCR(String logstart, Context context, BroadcastReceiver bcr) {
        try {
            if (bcr != null) {
                context.unregisterReceiver(bcr);
            }
        }
        catch(RuntimeException e) {
            Log.d(logstart, "rt while try to unregister bc receiver: "
                    + e.getLocalizedMessage());
        }
    }

    static String makeValidFolderName(CharSequence rootFolder, CharSequence ownerName) {
        // substitute special chars
        String folderName = Utils.url2FileName(ownerName.toString());
        Log.d("Util", "create folder for "
                + ownerName + " | use folderName: " + folderName);

        return rootFolder + "/" + folderName;
    }

    public static File getASAPRootDirectory(
            Context ctx, CharSequence rootFolder, CharSequence owner) {

        File dir = ctx.getExternalFilesDir(Util.makeValidFolderName(rootFolder, owner));

        Log.d("Util", "try: " + dir.getAbsolutePath());
        try {
            dir.mkdirs();
            return dir;
        }
        catch(RuntimeException e) {
            Log.d("Util", "cannot create directory: " + dir.getAbsolutePath()
                    + " | " + e.getLocalizedMessage() + "try rootFolder only");
        }

        Log.d("Util", "try: getExternalFilesDir(null)" + dir.getAbsolutePath());
        return ctx.getExternalFilesDir(null);
    }

    public static String getLogStart(Object o) {
        return o.getClass().getSimpleName();
    }

    private String getLogStart() {
        return Util.getLogStart(this);
    }
}
