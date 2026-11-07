package orbital.android;

import android.annotation.*;
import android.os.*;
import arc.*;
import com.android.dex.*;
import com.android.dx.cf.direct.*;
import com.android.dx.command.dexer.*;
import com.android.dx.dex.*;
import com.android.dx.dex.cf.*;
import com.android.dx.dex.file.DexFile;
import com.android.dx.merge.*;
import dalvik.system.*;
import rhino.*;

import java.io.*;
import java.nio.*;

/**
 * Helps to prepare a Rhino Context for usage on Android.
 *
 * <p>Rhino generates Java bytecode at runtime; Android runs DEX, so the generated
 * classes must be translated with the Dalvik DX tool and loaded through a
 * {@link DexClassLoader} (or {@link InMemoryDexClassLoader} on API 26+).</p>
 *
 * <p>Adapted from Mindustry's {@code mindustry.android.AndroidRhinoContext}
 * (GPLv3), originally by F43nd1r.</p>
 */
public class AndroidRhinoContext{

    /**
     * Call this instead of {@link Context#enter()}.
     * @return a context prepared for Android.
     */
    public static Context enter(File cacheDirectory){
        AndroidContextFactory factory;
        if(!ContextFactory.hasExplicitGlobal()){
            factory = new AndroidContextFactory(cacheDirectory);
            ContextFactory.getGlobalSetter().setContextFactoryGlobal(factory);
        }else if(!(ContextFactory.getGlobal() instanceof AndroidContextFactory)){
            throw new IllegalStateException("Cannot initialize factory for Android Rhino: There is already another factory");
        }else{
            factory = (AndroidContextFactory)ContextFactory.getGlobal();
        }

        return factory.enterContext();
    }

    /**
     * Ensures that the classloader used by Rhino can define classes on Android.
     */
    public static class AndroidContextFactory extends ContextFactory{
        private final File cacheDirectory;

        public AndroidContextFactory(File cacheDirectory){
            this.cacheDirectory = cacheDirectory;
            initApplicationClassLoader(createClassLoader(AndroidContextFactory.class.getClassLoader()));
        }

        @Override
        public BaseAndroidClassLoader createClassLoader(ClassLoader parent){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                return new InMemoryAndroidClassLoader(parent);
            }
            return new FileAndroidClassLoader(parent, cacheDirectory);
        }

        @Override
        protected void onContextReleased(final Context cx){
            super.onContextReleased(cx);
            ((BaseAndroidClassLoader)cx.getApplicationClassLoader()).reset();
        }
    }

    /**
     * Compiles Java bytecode to DEX and loads it.
     */
    abstract static class BaseAndroidClassLoader extends ClassLoader implements GeneratedClassLoader{

        public BaseAndroidClassLoader(ClassLoader parent){
            super(parent);
        }

        @Override
        public Class<?> defineClass(String name, byte[] data){
            try{
                DexOptions dexOptions = new DexOptions();
                DexFile dexFile = new DexFile(dexOptions);
                DirectClassFile classFile = new DirectClassFile(data, name.replace('.', '/') + ".class", true);
                classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
                classFile.getMagic();
                DxContext context = new DxContext();
                dexFile.add(CfTranslator.translate(context, classFile, null, new CfOptions(), dexOptions, dexFile));
                Dex dex = new Dex(dexFile.toDex(null, false));
                Dex oldDex = getLastDex();
                if(oldDex != null){
                    dex = new DexMerger(new Dex[]{dex, oldDex}, CollisionPolicy.KEEP_FIRST, context).merge();
                }
                return loadClass(dex, name);
            }catch(IOException | ClassNotFoundException e){
                throw new RuntimeException("Failed to define class", e);
            }
        }

        protected abstract Class<?> loadClass(Dex dex, String name) throws ClassNotFoundException;

        protected abstract Dex getLastDex();

        protected abstract void reset();

        @Override
        public void linkClass(Class<?> aClass){
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
            Class<?> loadedClass = findLoadedClass(name);
            if(loadedClass == null){
                Dex dex = getLastDex();
                if(dex != null){
                    loadedClass = loadClass(dex, name);
                }
                if(loadedClass == null){
                    loadedClass = getParent().loadClass(name);
                }
            }
            return loadedClass;
        }
    }

    static class FileAndroidClassLoader extends BaseAndroidClassLoader{
        private static int instanceCounter = 0;
        private final File dexFile;

        public FileAndroidClassLoader(ClassLoader parent, File cacheDir){
            super(parent);
            int id = instanceCounter++;
            dexFile = new File(cacheDir, id + ".dex");
            cacheDir.mkdirs();
            reset();
        }

        @Override
        protected Class<?> loadClass(Dex dex, String name) throws ClassNotFoundException{
            try{
                dex.writeTo(dexFile);
            }catch(IOException e){
                e.printStackTrace();
            }

            var context = (android.content.Context)Core.app;
            return new DexClassLoader(dexFile.getPath(), context.getCodeCacheDir().getPath(), null, getParent()).loadClass(name);
        }

        @Override
        protected Dex getLastDex(){
            if(dexFile.exists()){
                try{
                    return new Dex(dexFile);
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void reset(){
            dexFile.delete();
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    static class InMemoryAndroidClassLoader extends BaseAndroidClassLoader{
        private Dex last;

        public InMemoryAndroidClassLoader(ClassLoader parent){
            super(parent);
        }

        @Override
        protected Class<?> loadClass(Dex dex, String name) throws ClassNotFoundException{
            last = dex;
            return new InMemoryDexClassLoader(ByteBuffer.wrap(dex.getBytes()), getParent()).loadClass(name);
        }

        @Override
        protected Dex getLastDex(){
            return last;
        }

        @Override
        protected void reset(){
            last = null;
        }
    }
}
