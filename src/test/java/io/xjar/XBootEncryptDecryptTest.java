package io.xjar;

import io.xjar.boot.XBoot;
import io.xjar.key.XKey;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XJar Spring Boot 3.1.5 + JDK 21 适配验证测试
 * 测试 Spring Boot Fat JAR 的加密/解密轮转（encrypt → decrypt → 内容还原）
 */
class XBootEncryptDecryptTest {

    private static final String TEST_PASSWORD = "test-xjar-password-2024";

    @TempDir
    Path tempDir;

    /**
     * 构造一个最小的 Spring Boot Fat JAR 结构
     */
    private File buildFakeSpringBootJar() throws Exception {
        File jar = tempDir.resolve("app.jar").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar))) {
            zos.setMethod(ZipOutputStream.STORED);

            // META-INF/MANIFEST.MF
            byte[] manifest = buildManifest();
            putStoredEntry(zos, "META-INF/MANIFEST.MF", manifest);

            // BOOT-INF/classes/ directory
            putDirEntry(zos, "BOOT-INF/");
            putDirEntry(zos, "BOOT-INF/classes/");

            // A fake compiled class file (content won't be real bytecode,
            // but for encryption/decryption round-trip it doesn't need to be)
            byte[] classBytes = "FakeClassBytes-App-XJar".getBytes();
            putStoredEntry(zos, "BOOT-INF/classes/com/example/AppMain.class", classBytes);

            // BOOT-INF/lib/ directory
            putDirEntry(zos, "BOOT-INF/lib/");

            // A fake nested lib jar
            byte[] libJar = buildMinimalJar();
            putStoredEntry(zos, "BOOT-INF/lib/some-lib-1.0.jar", libJar);

            // org/ (Spring Boot loader classes - just a placeholder directory)
            putDirEntry(zos, "org/");
            putDirEntry(zos, "org/springframework/");
        }
        return jar;
    }

    private byte[] buildManifest() throws Exception {
        Manifest mf = new Manifest();
        Attributes attrs = mf.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Main-Class"), "org.springframework.boot.loader.JarLauncher");
        attrs.put(new Attributes.Name("Start-Class"), "com.example.Application");
        attrs.put(new Attributes.Name("Spring-Boot-Version"), "3.1.5");
        attrs.put(new Attributes.Name("Spring-Boot-Classes"), "BOOT-INF/classes/");
        attrs.put(new Attributes.Name("Spring-Boot-Lib"), "BOOT-INF/lib/");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        mf.write(bos);
        return bos.toByteArray();
    }

    private byte[] buildMinimalJar() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            byte[] content = "FakeLibClass".getBytes();
            ZipEntry e = new ZipEntry("com/example/Lib.class");
            e.setSize(content.length);
            e.setMethod(ZipEntry.STORED);
            CRC32 crc = new CRC32();
            crc.update(content);
            e.setCrc(crc.getValue());
            zos.putNextEntry(e);
            zos.write(content);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private void putDirEntry(ZipOutputStream zos, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(0);
        entry.setCrc(0);
        entry.setMethod(ZipEntry.STORED);
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    private void putStoredEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(data.length);
        entry.setCrc(crc.getValue());
        entry.setMethod(ZipEntry.STORED);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    @Test
    void testBootJarEncryptionChangesContent() throws Exception {
        File srcJar = buildFakeSpringBootJar();
        File encJar = tempDir.resolve("app.encrypted.jar").toFile();

        XKey key = XKit.key(TEST_PASSWORD);
        XBoot.encrypt(srcJar, encJar, key);

        assertTrue(encJar.exists(), "加密后 JAR 文件应存在");
        assertTrue(encJar.length() > 0, "加密后 JAR 文件不应为空");

        // 加密后内容应与原始内容不同
        assertFalse(Arrays.equals(
                readAllBytes(srcJar), readAllBytes(encJar)
        ), "加密后 JAR 内容应与原始内容不同");
    }

    @Test
    void testBootJarEncryptionContainsXjarIndex() throws Exception {
        File srcJar = buildFakeSpringBootJar();
        File encJar = tempDir.resolve("app.encrypted.jar").toFile();

        XKey key = XKit.key(TEST_PASSWORD);
        XBoot.encrypt(srcJar, encJar, key);

        // 加密后 JAR 应包含 XJAR-INF/INDEXES.IDX
        Set<String> entryNames = listJarEntries(encJar);
        assertTrue(
                entryNames.stream().anyMatch(n -> n.contains("XJAR-INF")),
                "加密 JAR 应包含 XJAR-INF 目录或索引文件，实际entries: " + entryNames
        );
    }

    @Test
    void testBootJarEncryptThenDecryptRestoresContent() throws Exception {
        File srcJar = buildFakeSpringBootJar();
        File encJar = tempDir.resolve("app.encrypted.jar").toFile();
        File decJar = tempDir.resolve("app.decrypted.jar").toFile();

        XKey key = XKit.key(TEST_PASSWORD);

        // 加密
        XBoot.encrypt(srcJar, encJar, key);
        assertTrue(encJar.exists(), "加密 JAR 应存在");

        // 解密
        XBoot.decrypt(encJar, decJar, key);
        assertTrue(decJar.exists(), "解密后 JAR 应存在");

        // 解密后的 BOOT-INF/classes 中的 class 文件内容应与原始内容一致
        byte[] originalClassBytes = extractEntry(srcJar, "BOOT-INF/classes/com/example/AppMain.class");
        byte[] decryptedClassBytes = extractEntry(decJar, "BOOT-INF/classes/com/example/AppMain.class");

        assertNotNull(originalClassBytes, "原始 JAR 应包含 class 文件");
        assertNotNull(decryptedClassBytes, "解密 JAR 应包含 class 文件");
        assertArrayEquals(originalClassBytes, decryptedClassBytes,
                "解密后 class 文件内容应与原始内容一致");
    }

    @Test
    void testBootJarMainClassReplacedWithXJarLauncher() throws Exception {
        File srcJar = buildFakeSpringBootJar();
        File encJar = tempDir.resolve("app.encrypted.jar").toFile();

        XKey key = XKit.key(TEST_PASSWORD);
        XBoot.encrypt(srcJar, encJar, key);

        // MANIFEST.MF 中的 Main-Class 应被替换为 io.xjar.boot.XJarLauncher
        try (JarArchiveInputStream jais = new JarArchiveInputStream(new FileInputStream(encJar))) {
            JarArchiveEntry entry;
            while ((entry = jais.getNextJarEntry()) != null) {
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
                    Manifest mf = new Manifest(jais);
                    String mainClass = mf.getMainAttributes().getValue("Main-Class");
                    assertEquals("io.xjar.boot.XJarLauncher", mainClass,
                            "加密后 Main-Class 应替换为 XJarLauncher");
                    String bootMainClass = mf.getMainAttributes().getValue("Boot-Main-Class");
                    assertEquals("org.springframework.boot.loader.JarLauncher", bootMainClass,
                            "Boot-Main-Class 应保存原始 Main-Class");
                    return;
                }
            }
        }
        fail("加密 JAR 中未找到 META-INF/MANIFEST.MF");
    }

    @Test
    void testKeyCreation() throws Exception {
        XKey key = XKit.key(TEST_PASSWORD);
        assertNotNull(key, "密钥对象不应为 null");
        assertNotNull(key.getAlgorithm(), "密钥算法不应为 null");
    }

    // --- helpers ---

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private Set<String> listJarEntries(File jar) throws IOException {
        Set<String> names = new HashSet<>();
        try (JarArchiveInputStream jais = new JarArchiveInputStream(new FileInputStream(jar))) {
            JarArchiveEntry entry;
            while ((entry = jais.getNextJarEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private byte[] extractEntry(File jar, String entryName) throws IOException {
        try (JarArchiveInputStream jais = new JarArchiveInputStream(new FileInputStream(jar))) {
            JarArchiveEntry entry;
            while ((entry = jais.getNextJarEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = jais.read(buf)) != -1) bos.write(buf, 0, n);
                    return bos.toByteArray();
                }
            }
        }
        return null;
    }
}
