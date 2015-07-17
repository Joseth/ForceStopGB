package me.piebridge.forcestopgb;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.saurik.substrate.MS;

import java.lang.reflect.Method;
import java.util.Set;

import me.piebridge.forcestopgb.common.CommonIntent;
import me.piebridge.forcestopgb.hook.Hook;
import me.piebridge.forcestopgb.hook.HookResult;
import me.piebridge.forcestopgb.hook.SystemHook;

public class SubstrateHook {

    private SubstrateHook() {

    }

    public static void initialize() {
        try {
            hookIntentFilter$match();
            hookActivityManagerService$startProcessLocked();
            hookActivity$onCreate();
            hookActivity$onDestroy();
            hookActivity$moveTaskToBack();
            hookActivity$startActivityForResult();
            hookProcess$killProcess();
        } catch (Throwable t) { // NOSONAR
            Log.d(CommonIntent.TAG, "cannot initialize", t);
        }
    }

    private static void hookActivityManagerService$startProcessLocked() { // NOSONAR
        MS.hookClassLoad("com.android.server.am.ActivityManagerService", new MS.ClassLoadHook() {
            @Override
            public void classLoaded(Class<?> ActivityManagerService) { // NOSONAR
                try {
                    hookActivityManagerService(ActivityManagerService);
                } catch (NoSuchMethodException e) { // NOSONAR
                    // do nothing
                } catch (ClassNotFoundException e) { // NOSONAR
                    // do nothing
                }
            }
        });
    }

    private static void hookActivityManagerService(Class<?> ActivityManagerService) throws ClassNotFoundException, NoSuchMethodException { // NOSONAR
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        SystemHook.setClassLoader(classLoader);

        Class<?> ProcessRecord = Class.forName("com.android.server.am.ProcessRecord", false, classLoader); // NOSONAR
        Method startProcessLocked;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            startProcessLocked = ActivityManagerService.getDeclaredMethod("startProcessLocked", ProcessRecord, String.class, String.class);
        } else {
            startProcessLocked = ActivityManagerService.getDeclaredMethod("startProcessLocked", ProcessRecord, String.class, String.class, String.class, String.class, String[].class);
        }
        MS.hookMethod(ActivityManagerService, startProcessLocked, new MS.MethodAlteration<Object, Void>() {
            @Override
            public Void invoked(Object thiz, Object... args) throws Throwable {
                if (!SystemHook.beforeActivityManagerService$startProcessLocked(args)) {
                    return null;
                } else {
                    return invoke(thiz, args);
                }
            }
        });
    }

    private static void hookIntentFilter$match() throws NoSuchMethodException { // NOSONAR
        Method IntentFilter$match = IntentFilter.class.getMethod("match", String.class, String.class, String.class, Uri.class, Set.class, String.class); // NOSONAR
        MS.hookMethod(IntentFilter.class, IntentFilter$match, new MS.MethodAlteration<IntentFilter, Integer>() {
            @Override
            public Integer invoked(IntentFilter thiz, Object... args) throws Throwable {
                HookResult result = SystemHook.hookIntentFilter$match(thiz, args);
                if (!result.isNone()) {
                    return (Integer) result.getResult();
                } else {
                    return invoke(thiz, args);
                }
            }
        });
    }

    private static void hookActivity$onCreate() throws NoSuchMethodException { // NOSONAR
        Method Activity$onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class); // NOSONAR
        MS.hookMethod(Activity.class, Activity$onCreate, new MS.MethodAlteration<Activity, Void>() {
            @Override
            public Void invoked(Activity thiz, Object... args) throws Throwable {
                Hook.beforeActivity$onCreate(thiz);
                return invoke(thiz, args);
            }
        });
    }

    private static void hookActivity$onDestroy() throws NoSuchMethodException { // NOSONAR
        Method Activity$onDestroy = Activity.class.getDeclaredMethod("onDestroy"); // NOSONAR
        MS.hookMethod(Activity.class, Activity$onDestroy, new MS.MethodAlteration<Activity, Void>() {
            @Override
            public Void invoked(Activity thiz, Object... args) throws Throwable {
                Hook.afterActivity$onDestroy(thiz);
                invoke(thiz, args);
                return null;
            }
        });
    }

    private static void hookActivity$moveTaskToBack() throws NoSuchMethodException { // NOSONAR
        Method Activity$moveTaskToBack = Activity.class.getMethod("moveTaskToBack", boolean.class); // NOSONAR
        MS.hookMethod(Activity.class, Activity$moveTaskToBack, new MS.MethodAlteration<Activity, Boolean>() {
            @Override
            public Boolean invoked(Activity thiz, Object... args) throws Throwable {
                Boolean result = invoke(thiz, args);
                Hook.afterActivity$moveTaskToBack(thiz, result);
                return result;
            }
        });
    }

    private static void hookActivity$startActivityForResult() throws NoSuchMethodException { // NOSONAR
        Method Activity$startActivityForResult = Activity.class.getMethod("startActivityForResult", Intent.class, int.class, Bundle.class); // NOSONAR
        MS.hookMethod(Activity.class, Activity$startActivityForResult, new MS.MethodAlteration<Activity, Void>() {
            @Override
            public Void invoked(Activity thiz, Object... args) throws Throwable {
                Intent intent = (Intent) args[0];
                if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
                    Hook.beforeActivity$startHomeActivityForResult(thiz);
                }
                return invoke(thiz, args);
            }
        });
    }

    private static void hookProcess$killProcess() throws NoSuchMethodException { // NOSONAR
        Method Process$killProcess = Process.class.getMethod("killProcess", int.class); // NOSONAR
        MS.hookMethod(Process.class, Process$killProcess, new MS.MethodAlteration<Process, Void>() {
            @Override
            public Void invoked(Process thiz, Object... args) throws Throwable {
                int pid = (Integer) args[0];
                if (Process.myPid() == pid) {
                    Hook.stopSelf(pid);
                }
                return invoke(thiz, args);
            }
        });
        Method System$exit = System.class.getMethod("exit", int.class); // NOSONAR
        MS.hookMethod(Process.class, System$exit, new MS.MethodAlteration<System, Void>() {
            @Override
            public Void invoked(System thiz, Object... args) throws Throwable {
                Hook.stopSelf(-1);
                return invoke(thiz, args);
            }
        });
    }

}
