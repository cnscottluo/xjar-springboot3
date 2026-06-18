package io.xjar.boot;

import io.xjar.XLauncher;
import org.springframework.boot.loader.launch.JarLauncher;

import java.util.Collection;
import java.net.URL;

/**
 * Spring-Boot Jar 启动器
 * 适配 Spring Boot 3.5.x: 使用 createClassLoader(Collection<URL>) 扩展点
 *
 * @author Payne 646742615@qq.com
 * 2018/11/23 23:06
 */
public class XJarLauncher extends JarLauncher {
    private final XLauncher xLauncher;

    public XJarLauncher(String... args) throws Exception {
        this.xLauncher = new XLauncher(args);
    }

    public static void main(String[] args) throws Exception {
        new XJarLauncher(args).launch();
    }

    public void launch() throws Exception {
        launch(xLauncher.args);
    }

    @Override
    protected ClassLoader createClassLoader(Collection<URL> urls) throws Exception {
        return new XBootClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader(),
                xLauncher.xDecryptor, xLauncher.xEncryptor, xLauncher.xKey);
    }

}
