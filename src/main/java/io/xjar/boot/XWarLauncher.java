package io.xjar.boot;

import io.xjar.XLauncher;
import org.springframework.boot.loader.WarLauncher;
import org.springframework.boot.loader.archive.Archive;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spring-Boot War 启动器
 * 适配 Spring Boot 3.x: 使用 Iterator<Archive> 替代已废弃的 URL[] 重载
 *
 * @author Payne 646742615@qq.com
 * 2018/11/23 23:06
 */
public class XWarLauncher extends WarLauncher {
    private final XLauncher xLauncher;

    public XWarLauncher(String... args) throws Exception {
        this.xLauncher = new XLauncher(args);
    }

    public static void main(String[] args) throws Exception {
        new XWarLauncher(args).launch();
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
