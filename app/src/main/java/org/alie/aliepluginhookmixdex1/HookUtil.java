package org.alie.aliepluginhookmixdex1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by Alie on 2019/10/6.
 * 类描述
 * 版本
 */
public class HookUtil {

    private static final String TAG = "HookUtil";
    private Context context;

    public void hookActivtyMh() {
        try {
            Class<?> classActivityThread = Class.forName("android.app.ActivityThread");
            Field fieldsCurrentActivityThread = classActivityThread.getDeclaredField("sCurrentActivityThread");
            fieldsCurrentActivityThread.setAccessible(true);
            Object objsCurrentActivityThread = fieldsCurrentActivityThread.get(null);

            Field fieldmH = classActivityThread.getDeclaredField("mH");
            fieldmH.setAccessible(true);
            Handler objmH = (Handler) fieldmH.get(objsCurrentActivityThread);
            /**
             * handler 源码中有
             *     public void dispatchMessage(Message msg) {
             *         if (msg.callback != null) {
             *             handleCallback(msg);
             *         } else {
             *             if (mCallback != null) {
             *                 if (mCallback.handleMessage(msg)) {
             *                     return;
             *                 }
             *             }
             *             handleMessage(msg);
             *         }
             *     }
             *  dispatchMessage 方法是handle最先调用的分发方法，里面有个mCallback，这个mCallback是个接口
             *  从代码中看，如果 if (mCallback != null) 成立，则执行接口方法，并且不会执行 handleMessage(msg)
             *  handleMessage(msg) 方法就是mH中重写后处理 msg消息的方法，所以，需要配合使用
             */


            Field fieldmCallback = Handler.class.getDeclaredField("mCallback");
            fieldmCallback.setAccessible(true);
            fieldmCallback.set(objmH,new ActivityJumpCallbcak(objmH));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void hookStartActivity(Context context) {
        this.context = context;
        try {
            Class<?> classActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
            Field fieldgDefault = classActivityManagerNative.getDeclaredField("gDefault");
            fieldgDefault.setAccessible(true);
            Object objgDefault = fieldgDefault.get(null);

            Class<?> classSingleton = Class.forName("android.util.Singleton");
            Field fieldmInstance = classSingleton.getDeclaredField("mInstance");
            fieldmInstance.setAccessible(true);
            Object objmInstance = fieldmInstance.get(objgDefault);

            Class<?> classIActivityManager = Class.forName("android.app.IActivityManager");

            /**
             * 开始动态代理啦：
             * ClassLoader loader,
             * Class<?>[] interfaces,代表要实现的hook对象的特征接口,这个传入目标类之后，
             * 在newProxyInstance返回的代理对象中就自动实现了 目标类中的接口了
             * InvocationHandler invocationHandler：分发方法来被调用，这个分发是什么意思？所有在 代理对象中实现的方法
             * 都会调用invocationHandler 中的involk方法，
             * 比如我调用startActivity方法，那么当我们设置完后代理对象后，starActivity方法就会走我们的InvocationHandler中的
             * invoke方法 来 并传入相应的 参数
             * 返回的objProxy就已经实现了classIActivityManager中的方法
             */
            Object objProxy = Proxy.newProxyInstance(
                    context.getClassLoader(),
                    new Class[]{classIActivityManager},
                    new StartActivityInvokeHandler(objmInstance));

            /**
             * 这一步操作，是反射中的替换，目的是将 gDefault(Singleton对象)中的mIntsance替换成
             * 我们的objProxy，怎么做呢？
             * fieldmInstance ：mInstance的原属性类
             * objgDefault： mInstance所属的那个类的对象
             * objProxy ：动态代理构造出来的类
             */
            fieldmInstance.set(objgDefault, objProxy);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private class ActivityJumpCallbcak implements Handler.Callback {

        private Handler objmH;

        public ActivityJumpCallbcak(Handler objmH) {
            this.objmH = objmH;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if(msg.what == 100) {


                Object obj = msg.obj;
                // 查看ActivityThread LAUNCH_ACTIVITY 后得知，
                // msg中的obj对象ActivityClientRecord就含有intent
                // 所以这里我们需要反射intent
                try {
                    // 开始获取最早的intent来进行替换
                    Field fieldintent =obj.getClass().getDeclaredField("intent");
                    fieldintent.setAccessible(true);
                    Intent newIntent = (Intent) fieldintent.get(obj);


                    Intent rawIntent = newIntent.getParcelableExtra("rawIntent");
                    newIntent.setComponent(rawIntent.getComponent());
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            objmH.handleMessage(msg);
            return false;
        }
    }


    private class StartActivityInvokeHandler implements InvocationHandler {

        private Object objmInstance;

        public StartActivityInvokeHandler(Object objmInstance) {
            this.objmInstance = objmInstance;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            Log.i(TAG, "====" + method.getName());

            Intent rawIntent = null;
            int index = 0;
            if ("startActivity".equals(method.getName())) {
                Log.i(TAG, "========" + method.getName() + "========");
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof Intent) {
                        rawIntent = (Intent) arg;
                        index = i;
                    }
                }
                Intent packageIntent = new Intent();
                packageIntent.setComponent(new ComponentName(context, ProxyActivity.class));
                packageIntent.putExtra("rawIntent", rawIntent);
                args[index] = packageIntent;
            }
            return method.invoke(objmInstance, args);
        }
    }


    /**
     * 通过反射，将pluginAPK中的Elements注入到宿主中
     */
    public void injectPluginDex(Context context) {
        this.context = context;
        String apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/playchess-debug.apk";
        String cachePath = context.getCacheDir().getAbsolutePath();
        DexClassLoader dexClassLoader = new DexClassLoader(apkPath, cachePath, cachePath, context.getClassLoader());
        // 第1步 获取插件Element元素
        try {
            Class<?> classPluginClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field fieldPluginPathList = classPluginClassLoader.getDeclaredField("pathList");
            fieldPluginPathList.setAccessible(true);
            Object objPathList = fieldPluginPathList.get(dexClassLoader);

            Class<?> classPluginPathList = objPathList.getClass();
            Field fieldPluginDexElements = classPluginPathList.getDeclaredField("dexElements");
            fieldPluginDexElements.setAccessible(true);
            Object objPluginElements = fieldPluginDexElements.get(objPathList);


            // 第2步 获取宿主Element元素
            // 获取宿主Elemnets时候，这里需要使PathClassLoader，之前我们已经看到了，context.getClassLoader();返回的是
            // 系统默认的PathClassLoader
            PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
            Class<?> classHostClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field fieldPathList = classHostClassLoader.getDeclaredField("pathList");
            fieldPathList.setAccessible(true);
            Object objHostPathList = fieldPathList.get(pathClassLoader);

            Class<?> classHostPathList = objHostPathList.getClass();
            Field fieldHostElements = classHostPathList.getDeclaredField("dexElements");
            fieldHostElements.setAccessible(true);
            Object objHostElements = fieldHostElements.get(objHostPathList);


            // 第3步 反射hook回去

            // 3.1 先组合一个数组集合
            int pluginElementLenth = Array.getLength(objPluginElements);
            int hostElementLenth = Array.getLength(objHostElements);
            int newElementLenth = hostElementLenth + pluginElementLenth;
            Class<?> sigleElementClazz = objHostElements.getClass().getComponentType();
            Object newElements = Array.newInstance(sigleElementClazz, newElementLenth);

            // 3.2 遍历融合
            for (int i = 0; i < newElementLenth; i++) {
                if (i < pluginElementLenth) {
                    Array.set(newElements, i, Array.get(objPluginElements, i));
                } else {
                    Array.set(newElements, i, Array.get(objHostElements, i - pluginElementLenth));
                }
            }
            fieldHostElements.set(objHostPathList, newElements);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
