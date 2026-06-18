package io.xjar;

import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * XJAR API - 将 XJar 框架 classes 注入到加密 JAR 包
 * 替代原来依赖 loadkit 的实现，改用标准 JDK 类路径扫描
 *
 * @author Payne 646742615@qq.com
 * 2018/11/25 10:34
 */
public class XInjector {

    private static final String XJAR_PACKAGE_PREFIX = "io/xjar/";

    /**
     * 往JAR包中注入XJar框架的classes
     *
     * @param zos jar包输出流
     * @throws IOException I/O 异常
     */
    public static void inject(JarArchiveOutputStream zos) throws IOException {
        // 定位包含 XInjector.class 的源 (JAR 或 类目录)
        URL location = XInjector.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            // 无法确定代码源，退出
            return;
        }
        URI uri;
        try {
            uri = location.toURI();
        } catch (Exception e) {
            throw new IOException("Cannot resolve XInjector code source location: " + location, e);
        }
        File source = new File(uri);

        if (source.isFile()) {
            // 源是 JAR 文件
            injectFromJar(zos, source);
        } else if (source.isDirectory()) {
            // 源是 classes 目录 (开发/测试环境)
            injectFromDirectory(zos, source, source);
        }
    }

    private static void injectFromJar(JarArchiveOutputStream zos, File jarFile) throws IOException {
        Set<String> directories = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(XJAR_PACKAGE_PREFIX)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    ensureDirectory(zos, directories, name);
                } else {
                    ensureDirectory(zos, directories, name.substring(0, name.lastIndexOf('/') + 1));
                    JarArchiveEntry xJarEntry = new JarArchiveEntry(name);
                    xJarEntry.setTime(entry.getTime());
                    zos.putArchiveEntry(xJarEntry);
                    try (InputStream is = jar.getInputStream(entry)) {
                        XKit.transfer(is, zos);
                    }
                    zos.closeArchiveEntry();
                }
            }
        }
    }

    private static void injectFromDirectory(JarArchiveOutputStream zos, File root, File dir) throws IOException {
        Set<String> directories = new HashSet<>();
        injectFromDirectoryRecursive(zos, root, dir, directories);
    }

    private static void injectFromDirectoryRecursive(JarArchiveOutputStream zos, File root, File dir,
                                                      Set<String> directories) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String relativePath = root.toURI().relativize(file.toURI()).getPath();
            if (!relativePath.startsWith(XJAR_PACKAGE_PREFIX)) {
                continue;
            }
            if (file.isDirectory()) {
                ensureDirectory(zos, directories, relativePath);
                injectFromDirectoryRecursive(zos, root, file, directories);
            } else {
                ensureDirectory(zos, directories, relativePath.substring(0, relativePath.lastIndexOf('/') + 1));
                JarArchiveEntry xJarEntry = new JarArchiveEntry(relativePath);
                xJarEntry.setTime(file.lastModified());
                zos.putArchiveEntry(xJarEntry);
                try (InputStream is = new FileInputStream(file)) {
                    XKit.transfer(is, zos);
                }
                zos.closeArchiveEntry();
            }
        }
    }

    private static void ensureDirectory(JarArchiveOutputStream zos, Set<String> directories, String dir) throws IOException {
        if (!dir.isEmpty() && directories.add(dir)) {
            JarArchiveEntry xDirEntry = new JarArchiveEntry(dir);
            xDirEntry.setTime(System.currentTimeMillis());
            zos.putArchiveEntry(xDirEntry);
            zos.closeArchiveEntry();
        }
    }
}
