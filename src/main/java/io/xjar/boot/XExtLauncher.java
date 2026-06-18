package io.xjar.boot;

import io.xjar.XLauncher;
import org.springframework.boot.loader.PropertiesLauncher;
import org.springframework.boot.loader.archive.Archive;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spring-Boot Properties 启动器
 * 适配 Spring Boot 3.x: createClassLoader 方法签名由 List<Archive> 改为 Iterator<Archive>
 *
 * @author Payne 646742615@qq.com
 * 2019/4/14 10:26
 */
public class XExtLauncher extends PropertiesLauncher {
    private final XLauncher xLauncher;

    public XExtLauncher(String... args) throws Exception {
        this.xLauncher = new XLauncher(args);
    }

    public static void main(String[] args) throws Exception {
        new XExtLauncher(args).launch();
    }

    public void launch() throws Exception {
        launch(xLauncher.args);
    }

    @Override
    protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
        List<URL> urls = new ArrayList<>();
        while (archives.hasNext()) {
            urls.add(archives.next().getUrl());
        }
        return new XBootClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader(),
                xLauncher.xDecryptor, xLauncher.xEncryptor, xLauncher.xKey);
    }
}
