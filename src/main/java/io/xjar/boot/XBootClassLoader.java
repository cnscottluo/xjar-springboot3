package io.xjar.boot;

import io.xjar.XDecryptor;
import io.xjar.XEncryptor;
import io.xjar.XKit;
import io.xjar.key.XKey;
import io.xjar.reflection.XReflection;
import org.springframework.boot.loader.LaunchedURLClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Enumeration;

/**
 * X类加载器
 * 适配 JDK 21: ucp 字段在 JDK 17+ 受强封装保护，通过 try-catch 优雅降级
 *
 * @author Payne 646742615@qq.com
 * 2018/11/23 23:04
 */
public class XBootClassLoader extends LaunchedURLClassLoader {
    private final XBootURLHandler xBootURLHandler;
    private final Object urlClassPath;
    private final Method getResource;
    private final Method getCodeSourceURL;
    private final Method getCodeSigners;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public XBootClassLoader(URL[] urls, ClassLoader parent, XDecryptor xDecryptor, XEncryptor xEncryptor, XKey xKey) throws Exception {
        super(urls, parent);
        this.xBootURLHandler = new XBootURLHandler(xDecryptor, xEncryptor, xKey, this);
        // JDK 17+ 强封装: ucp 字段不可反射访问，优雅降级以保证兼容 JDK 21
        Object ucpTemp = null;
        Method getResourceTemp = null;
        Method getCodeSourceURLTemp = null;
        Method getCodeSignersTemp = null;
        try {
            ucpTemp = XReflection.field(URLClassLoader.class, "ucp").get(this).value();
            getResourceTemp = XReflection.method(ucpTemp.getClass(), "getResource", String.class).method();
            getCodeSourceURLTemp = XReflection.method(getResourceTemp.getReturnType(), "getCodeSourceURL").method();
            getCodeSignersTemp = XReflection.method(getResourceTemp.getReturnType(), "getCodeSigners").method();
        } catch (Exception e) {
            // JDK 21 模块化强封装: 无法访问 ucp 字段，fallback 模式下不附加 CodeSource
        }
        this.urlClassPath = ucpTemp;
        this.getResource = getResourceTemp;
        this.getCodeSourceURL = getCodeSourceURLTemp;
        this.getCodeSigners = getCodeSignersTemp;
    }

    @Override
    public URL findResource(String name) {
        URL url = super.findResource(name);
        if (url == null) {
            return null;
        }
        try {
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile(), xBootURLHandler);
        } catch (MalformedURLException e) {
            return url;
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> enumeration = super.findResources(name);
        if (enumeration == null) {
            return null;
        }
        return new XBootEnumeration(enumeration);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassFormatError e) {
            String path = name.replace('.', '/').concat(".class");
            URL url = findResource(path);
            if (url == null) {
                throw new ClassNotFoundException(name, e);
            }
            try (InputStream in = url.openStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                XKit.transfer(in, bos);
                byte[] bytes = bos.toByteArray();
                if (urlClassPath != null && getResource != null) {
                    Object resource = getResource.invoke(urlClassPath, path);
                    if (resource != null) {
                        URL codeSourceURL = (URL) getCodeSourceURL.invoke(resource);
                        CodeSigner[] codeSigners = (CodeSigner[]) getCodeSigners.invoke(resource);
                        CodeSource codeSource = new CodeSource(codeSourceURL, codeSigners);
                        return defineClass(name, bytes, 0, bytes.length, codeSource);
                    }
                }
                // JDK 21 fallback: defineClass without CodeSource
                return defineClass(name, bytes, 0, bytes.length);
            } catch (Throwable t) {
                throw new ClassNotFoundException(name, t);
            }
        }
    }

    private class XBootEnumeration implements Enumeration<URL> {
        private final Enumeration<URL> enumeration;

        XBootEnumeration(Enumeration<URL> enumeration) {
            this.enumeration = enumeration;
        }

        @Override
        public boolean hasMoreElements() {
            return enumeration.hasMoreElements();
        }

        @Override
        public URL nextElement() {
            URL url = enumeration.nextElement();
            if (url == null) {
                return null;
            }
            try {
                return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile(), xBootURLHandler);
            } catch (MalformedURLException e) {
                return url;
            }
        }
    }
}
