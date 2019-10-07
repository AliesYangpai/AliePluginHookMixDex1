package org.alie.aliepluginhookmixdex1;

import android.content.Context;
import android.os.Environment;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by Alie on 2019/10/6.
 * 类描述
 * 版本
 */
public class HookUtil {


    /**
     * 通过反射，将pluginAPK中的Elements注入到宿主中
     */
    public void injectPluginDex(Context context) {
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


    public void injectResource() {

    }
}
