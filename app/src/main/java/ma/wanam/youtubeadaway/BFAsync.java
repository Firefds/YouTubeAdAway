package ma.wanam.youtubeadaway;

import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.regex.Pattern;

import dalvik.system.DexFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class BFAsync extends AsyncTask<LoadPackageParam, Void, Boolean> {
    private static ClassLoader cl;
    private static boolean found = false;

    private void debug(String msg) {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(msg);
        }
    }

    private void debug(Throwable msg) {
        if (BuildConfig.DEBUG) {
            XposedBridge.log(msg);
        }
    }

    @Override
    protected Boolean doInBackground(LoadPackageParam... params) {
        cl = params[0].classLoader;

        String[] allCl = getAllClasses(params[0].appInfo);

        for (String clName : allCl) {
            if (clName.length() < 5) {
                findAndHookYouTubeAds(clName);
            }
        }

        return found;
    }

    private static String[] getAllClasses(ApplicationInfo ai) {
        ArrayList<String> classes = new ArrayList<>();
        try {
            XposedBridge.log(">>>>>>>>>> sourceDir: " + ai.sourceDir);
            DexFile df = new DexFile(ai.sourceDir);
            int iCnt = 0;
            for (Enumeration<String> iter = df.entries(); iter.hasMoreElements(); ) {
                String className = iter.nextElement();
                iCnt++;
                classes.add(className);
            }

            XposedBridge.log(">>>>>>>>>> count: " + classes.size() + "/" + iCnt);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return toStringArray(classes);
    }

    private static String[] toStringArray(ArrayList<String> classes) {
        String[] array = new String[classes.size()];
        for (int i = 0; i < classes.size(); i++) {
            array[i] = classes.get(i);
        }
        return array;
    }


    /**
     * @param cls
     * @return true if a hook was found
     */
    private void findAndHookYouTubeAds(String cls) {
        Class<?> classObj;
        Class<?> paramObj;
        final String lCRegex = "[a-z]+";
        final Pattern lCPatern = Pattern.compile(lCRegex);

        try {
            classObj = XposedHelpers.findClass(cls, cl);
        } catch (Throwable e1) {
            return;
        }

        try {
            XposedHelpers.findConstructorExact(classObj);
            XposedHelpers.findConstructorExact(classObj, Parcel.class);
            if (XposedHelpers.findFirstFieldByExactType(classObj, Parcelable.Creator.class).getName().equals("CREATOR")) {
                try {
                    Method[] methods = classObj.getDeclaredMethods();
                    for (Method m : methods) {
                        if (m.getReturnType().equals(boolean.class) && m.getParameterTypes().length == 1) {
                            paramObj = m.getParameterTypes()[0];

                            if (lCPatern.matcher(paramObj.getName()).matches()) {
                                Method mClass = XposedHelpers.findMethodExact(classObj, "a", paramObj);

                                if (mClass.getReturnType().equals(boolean.class)) {
                                    try {
                                        XposedBridge.hookAllConstructors(classObj, new XC_MethodHook() {
                                            @Override
                                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                                Field[] fields = param.thisObject.getClass().getDeclaredFields();
                                                for (Field f : fields) {
                                                    if (f.getType().equals(long.class)) {
                                                        long timestamp = Calendar.getInstance().getTimeInMillis();
                                                        debug("class: " + param.thisObject.getClass().getName());
                                                        debug("set expiry timestamp: " + f.getName() + " = " + timestamp);
                                                        XposedHelpers.setLongField(param.thisObject, f.getName(), timestamp);
                                                        break;
                                                    }
                                                }
                                            }
                                        });

                                        found = true;

                                        debug("YouTube AdAway: Successfully hooked ads wrapper " + classObj.getName()
                                                + " param=" + paramObj.getName());
                                    } catch (Throwable e) {
                                        debug("YouTube AdAway: Failed to hook " + classObj.getName()
                                                + " param=" + paramObj.getName() + " error: " + e);
                                    }
                                }
                            }
                        }
                    }

                } catch (Throwable e) {
                    debug("YouTube AdAway: Failed to hook " + classObj.getName() + " methods!");
                    debug(e);
                }
            }
        } catch (Throwable e) {
        }
    }

    @Override
    protected void onPostExecute(Boolean found) {

        if (!found) {
            XposedBridge.log("YouTube AdAway: brute force failed!");
        }
    }

}
